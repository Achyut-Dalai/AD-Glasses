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

/// Native spoken output for short assistant/translation responses.
///
/// `speechVoices()` reports what is available on this iPhone. The app never assumes Ava, Zoe,
/// Samantha, or Alex is installed and does not try to bundle or download a voice itself.
@MainActor
final class SpeechOutputController: NSObject, ObservableObject {
    @Published private(set) var voices: [SpeechVoiceOption] = []
    @Published private(set) var isSpeaking = false
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

    func speak(_ text: String, languageCode: String? = nil) throws {
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

        do {
            try audioSession.setCategory(
                .playback,
                mode: .spokenAudio,
                options: [.allowBluetoothA2DP]
            )
            try audioSession.setActive(true)
        } catch {
            throw SpeechOutputError.audioRouteUnavailable(error.localizedDescription)
        }

        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        let utterance = AVSpeechUtterance(string: value)
        utterance.voice = voice
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        isSpeaking = false
        try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
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
        isSpeaking = true
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didFinish utterance: AVSpeechUtterance
    ) {
        isSpeaking = false
        try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
    }

    func speechSynthesizer(
        _ synthesizer: AVSpeechSynthesizer,
        didCancel utterance: AVSpeechUtterance
    ) {
        isSpeaking = false
        try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
    }
}
