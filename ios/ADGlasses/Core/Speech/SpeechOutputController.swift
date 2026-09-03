import AVFoundation
import Combine
import Foundation

enum SpeechVoiceQuality: Int, Comparable, Sendable {
    case standard = 1
    case enhanced = 2
    case premium = 3

    static func < (lhs: SpeechVoiceQuality, rhs: SpeechVoiceQuality) -> Bool {
        lhs.rawValue < rhs.rawValue
    }

    var label: String {
        switch self {
        case .standard: return "Standard"
        case .enhanced: return "Enhanced"
        case .premium: return "Premium"
        }
    }
}

struct SpeechVoiceOption: Identifiable, Equatable, Sendable {
    var id: String { identifier }

    let identifier: String
    let name: String
    let language: String
    let quality: SpeechVoiceQuality
}

enum SpeechOutputAudioSessionPolicy: Sendable {
    /// Normal Assistant speech no longer needs a recording route after recognition has finished.
    /// A playback session lets iOS retain/select the glasses' normal A2DP output instead of forcing
    /// the iPhone speaker merely because an HFP microphone is unavailable.
    case managedPlayback
    /// Live Translation owns a simultaneous play-and-record Bluetooth session. Speech output must
    /// reuse that full-duplex route instead of changing categories between every translated turn.
    case reuseCurrentSession
}

enum SpeechOutputError: LocalizedError {
    case noVoiceAvailable
    case audioRouteUnavailable(String)

    var errorDescription: String? {
        switch self {
        case .noVoiceAvailable:
            return "No installed Apple speech voice supports this language."
        case .audioRouteUnavailable(let reason):
            return "Could not prepare speech playback: \(reason)"
        }
    }
}

/// Native spoken output for Assistant and translation responses.
///
/// `speechVoices()` reports what is actually installed on this iPhone. AD does not bundle or
/// silently download Apple voices. Bluetooth routing remains an iOS system route: AD makes A2DP/HFP
/// eligible and avoids speaker-forcing overrides, but it never pretends CoreBluetooth can select a
/// Classic-Bluetooth audio device.
@MainActor
final class SpeechOutputController: NSObject, ObservableObject {
    @Published private(set) var voices: [SpeechVoiceOption] = []
    @Published private(set) var isSpeaking = false
    @Published private(set) var outputRouteName = "Not active"
    @Published private(set) var outputRouteIsBluetooth = false
    @Published var selectedVoiceIdentifier: String {
        didSet {
            guard selectedVoiceIdentifier != oldValue else { return }
            defaults.set(selectedVoiceIdentifier, forKey: selectedVoiceKey)
        }
    }

    private let synthesizer: AVSpeechSynthesizer
    private let defaults: UserDefaults
    private let audioSession: AVAudioSession
    private let selectedVoiceKey = "speech.output.selectedVoiceIdentifier.v1"
    private var queuedUtterances: [AVSpeechUtterance] = []
    private var ownsAudioSession = false

    init(
        synthesizer: AVSpeechSynthesizer = AVSpeechSynthesizer(),
        defaults: UserDefaults = .standard,
        audioSession: AVAudioSession = .sharedInstance()
    ) {
        self.synthesizer = synthesizer
        self.defaults = defaults
        self.audioSession = audioSession
        selectedVoiceIdentifier = defaults.string(
            forKey: "speech.output.selectedVoiceIdentifier.v1"
        ) ?? ""
        super.init()
        synthesizer.delegate = self
        refreshVoices()
        refreshOutputRouteName()
    }

    func refreshVoices() {
        voices = AVSpeechSynthesisVoice.speechVoices()
            .map(Self.option)
            .sorted(by: Self.sortVoices)

        guard voices.contains(where: { $0.identifier == selectedVoiceIdentifier }) else {
            selectedVoiceIdentifier = preferredVoice(languageCode: Locale.current.language.languageCode?.identifier)?.identifier
                ?? voices.first?.identifier
                ?? ""
            return
        }
    }

    func refreshOutputRouteName() {
        let outputs = audioSession.currentRoute.outputs
        outputRouteName = outputs.first?.portName ?? "No audio output"
        outputRouteIsBluetooth = outputs.contains {
            SpeechInputAudioSession.isBluetoothOutputPort($0.portType)
        }
    }

    func speak(
        _ text: String,
        languageCode: String? = nil,
        audioSessionPolicy: SpeechOutputAudioSessionPolicy = .managedPlayback
    ) throws {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }

        let option: SpeechVoiceOption?
        if let languageCode,
           let selected = voices.first(where: { $0.identifier == selectedVoiceIdentifier }),
           Self.language(selected.language, matches: languageCode) {
            option = selected
        } else if let languageCode {
            option = preferredVoice(languageCode: languageCode)
        } else {
            option = voices.first(where: { $0.identifier == selectedVoiceIdentifier })
                ?? preferredVoice(languageCode: Locale.current.language.languageCode?.identifier)
        }
        guard let option,
              let voice = AVSpeechSynthesisVoice(identifier: option.identifier) else {
            throw SpeechOutputError.noVoiceAvailable
        }

        if synthesizer.isSpeaking || !queuedUtterances.isEmpty {
            // Clear ownership before stopping. AVSpeechSynthesizer can deliver didCancel later;
            // that stale callback must not deactivate the audio session used by this new reply.
            queuedUtterances.removeAll()
            if ownsAudioSession {
                deactivateAudioSession()
            }
            ownsAudioSession = false
            synthesizer.stopSpeaking(at: .immediate)
        }
        try enqueue(value, voice: voice, audioSessionPolicy: audioSessionPolicy)
    }

    /// Adds a safe streaming segment without interrupting speech already in progress.
    func enqueue(
        _ text: String,
        languageCode: String? = nil,
        audioSessionPolicy: SpeechOutputAudioSessionPolicy = .managedPlayback
    ) throws {
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        let option = languageCode.flatMap(preferredVoice(languageCode:))
            ?? voices.first(where: { $0.identifier == selectedVoiceIdentifier })
            ?? preferredVoice(languageCode: Locale.current.language.languageCode?.identifier)
        guard let option,
              let voice = AVSpeechSynthesisVoice(identifier: option.identifier) else {
            throw SpeechOutputError.noVoiceAvailable
        }
        try enqueue(value, voice: voice, audioSessionPolicy: audioSessionPolicy)
    }

    func stop() {
        queuedUtterances.removeAll()
        synthesizer.stopSpeaking(at: .immediate)
        settleIdleState()
    }

    func preferredVoice(languageCode: String?) -> SpeechVoiceOption? {
        let matching = voices.filter { voice in
            guard let languageCode else { return true }
            return Self.language(voice.language, matches: languageCode)
        }
        return matching.max { lhs, rhs in
            Self.preferenceScore(lhs) < Self.preferenceScore(rhs)
        }
    }

    private func activateManagedAudioSession() throws {
        // Recognition has already ended for normal Assistant output, so don't keep a microphone
        // category alive just to synthesize speech. `.playback` automatically supports A2DP and is
        // the cleanest way to preserve the user's selected glasses route. There is intentionally no
        // speaker override; with no Bluetooth device iOS naturally falls back to the phone speaker.
        try? audioSession.overrideOutputAudioPort(.none)
        try? audioSession.setPreferredInput(nil)
        try audioSession.setCategory(
            .playback,
            mode: .voicePrompt,
            options: [.duckOthers]
        )
        try audioSession.setActive(true)
        ownsAudioSession = true
        refreshOutputRouteName()
    }

    private func deactivateAudioSession() {
        try? audioSession.overrideOutputAudioPort(.none)
        try? audioSession.setPreferredInput(nil)
        try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        refreshOutputRouteName()
    }

    private func enqueue(
        _ text: String,
        voice: AVSpeechSynthesisVoice,
        audioSessionPolicy: SpeechOutputAudioSessionPolicy
    ) throws {
        if queuedUtterances.isEmpty {
            switch audioSessionPolicy {
            case .managedPlayback:
                do {
                    try activateManagedAudioSession()
                } catch {
                    if ownsAudioSession {
                        deactivateAudioSession()
                    }
                    ownsAudioSession = false
                    isSpeaking = false
                    throw SpeechOutputError.audioRouteUnavailable(error.localizedDescription)
                }
            case .reuseCurrentSession:
                // Live Translation already established a play-and-record session with both HFP and
                // A2DP eligible. Never replace it with a speaker-biased category here.
                ownsAudioSession = false
                refreshOutputRouteName()
            }
        }

        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        queuedUtterances.append(utterance)
        isSpeaking = true
        synthesizer.speak(utterance)
    }

    private func finish(_ utterance: AVSpeechUtterance, allowContentMatch: Bool) {
        var index = queuedUtterances.firstIndex(where: { $0 === utterance })

        // AVSpeechSynthesizer normally returns the same utterance object to its delegate. If an
        // engine/OS version instead bridges a different object for a successful completion, match
        // the serial queue by speech content so the product cannot remain stuck in "Speaking".
        // Cancellation callbacks deliberately do not use this fallback because a stale didCancel
        // from an interrupted reply must never consume a newly queued utterance with similar text.
        if index == nil, allowContentMatch {
            index = queuedUtterances.firstIndex(where: { $0.speechString == utterance.speechString })
        }

        if let index {
            queuedUtterances.remove(at: index)
        } else {
            // A callback can arrive after stop()/replacement cleared the old queue. Leave a new
            // active queue alone; if the synthesizer itself is now idle, reconcile our published
            // state as well so Stop/Ask UI cannot stay latched forever.
            guard !synthesizer.isSpeaking, !synthesizer.isPaused else { return }
            queuedUtterances.removeAll()
        }

        guard queuedUtterances.isEmpty else { return }
        settleIdleState()
    }

    private func settleIdleState() {
        isSpeaking = false
        if ownsAudioSession {
            deactivateAudioSession()
        } else {
            refreshOutputRouteName()
        }
        ownsAudioSession = false
    }

    private static func option(_ voice: AVSpeechSynthesisVoice) -> SpeechVoiceOption {
        let quality: SpeechVoiceQuality
        switch voice.quality {
        case .premium: quality = .premium
        case .enhanced: quality = .enhanced
        default: quality = .standard
        }
        return SpeechVoiceOption(
            identifier: voice.identifier,
            name: voice.name,
            language: voice.language,
            quality: quality
        )
    }

    private static func sortVoices(_ lhs: SpeechVoiceOption, _ rhs: SpeechVoiceOption) -> Bool {
        if lhs.language != rhs.language {
            return lhs.language.localizedCaseInsensitiveCompare(rhs.language) == .orderedAscending
        }
        if lhs.quality != rhs.quality { return lhs.quality > rhs.quality }
        return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
    }

    private static func preferenceScore(_ voice: SpeechVoiceOption) -> Int {
        let preferredNames = ["Ava", "Zoe", "Samantha", "Alex"]
        let nameBonus: Int
        if let index = preferredNames.firstIndex(where: {
            voice.name.localizedCaseInsensitiveCompare($0) == .orderedSame
        }) {
            nameBonus = preferredNames.count - index
        } else {
            nameBonus = 0
        }
        return voice.quality.rawValue * 100 + nameBonus
    }

    private static func language(_ voiceLanguage: String, matches requested: String) -> Bool {
        let voice = voiceLanguage.lowercased()
        let request = requested.lowercased()
        return voice == request ||
            voice.split(separator: "-").first == request.split(separator: "-").first
    }
}

@MainActor
extension SpeechOutputController: @preconcurrency AVSpeechSynthesizerDelegate {
    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didStart utterance: AVSpeechUtterance
    ) {
        guard queuedUtterances.contains(where: { $0 === utterance }) ||
                queuedUtterances.contains(where: { $0.speechString == utterance.speechString }) else { return }
        isSpeaking = true
        refreshOutputRouteName()
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        finish(utterance, allowContentMatch: true)
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didCancel utterance: AVSpeechUtterance
    ) {
        finish(utterance, allowContentMatch: false)
    }
}
