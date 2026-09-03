import AVFoundation
import Foundation
import FoundationModels

#if canImport(CoreAILanguageModels) && !targetEnvironment(simulator)
import CoreAILanguageModels
#endif

/// SpeechAnalyzer-only factory for AD voice input.
///
/// AD Glasses targets iOS 27+, so there is no legacy recognizer, older-OS fallback, or runtime
/// availability branch. Model preparation/download failures stay inside the SpeechAnalyzer lifecycle
/// instead of silently switching engines.
enum AppleSpeechTranscriber {
    /// Assistant voice input is an English product surface today. Do not inherit `Locale.current`:
    /// a device region such as India can otherwise silently select en-IN and force a different
    /// speech asset than the app actually intends to use.
    static let assistantLocale = Locale(identifier: "en-US")

    @MainActor
    static func make(locale: Locale = assistantLocale) -> any SpeechTranscribing {
        // Prewarm is best effort. The Core AI model is deliberately not required for speech itself;
        // if the model has not been staged on this development device, verified SpeechAnalyzer and
        // deterministic Assistant routing continue to work exactly as before.
        Task { await LocalAssistantSemanticRepair.shared.prewarm() }
        return SemanticRepairingSpeechTranscriber(locale: locale)
    }
}

/// Wraps SpeechAnalyzer at the product boundary rather than teaching the speech engine about
/// glasses commands. Only finalized *external glasses PCM* is eligible for semantic repair. Phone
/// dictation and ordinary chat text are never rewritten by this layer.
@MainActor
private final class SemanticRepairingSpeechTranscriber: ExternalAudioSpeechTranscribing {
    private enum InputMode {
        case phone
        case externalGlassesPCM
    }

    private let base: SpeechAnalyzerTranscriber
    private var inputMode: InputMode?
    private var terminalRepairTask: Task<Void, Never>?

    let engineName: String
    private(set) var snapshot: SpeechTranscriptionSnapshot {
        didSet { onUpdate?(snapshot) }
    }
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    init(locale: Locale) {
        let base = SpeechAnalyzerTranscriber(locale: locale)
        self.base = base
        engineName = base.engineName
        snapshot = base.snapshot

        base.onUpdate = { [weak self] next in
            self?.consumeBaseUpdate(next)
        }
        base.onError = { [weak self] error in
            self?.onError?(error)
        }
    }

    func start() async throws {
        await finishPendingTerminalRepair()
        inputMode = nil
        try await base.start()
        inputMode = .phone
        snapshot = base.snapshot
    }

    func stop() async {
        let mode = inputMode
        await base.stop()
        if mode == .externalGlassesPCM {
            await finishPendingTerminalRepair()
        } else {
            inputMode = nil
        }
    }

    func resetTranscript() {
        terminalRepairTask?.cancel()
        terminalRepairTask = nil
        base.resetTranscript()
        snapshot = base.snapshot
    }

    func startExternalAudio() async throws {
        await finishPendingTerminalRepair()
        inputMode = nil
        try await base.startExternalAudio()
        inputMode = .externalGlassesPCM
        snapshot = base.snapshot
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        base.appendExternalAudio(buffer)
    }

    func finishExternalAudio() async {
        guard inputMode == .externalGlassesPCM else { return }
        await base.finishExternalAudio()
        await finishPendingTerminalRepair()
    }

    private func consumeBaseUpdate(_ next: SpeechTranscriptionSnapshot) {
        // Hold the terminal external-PCM update long enough to perform one bounded semantic repair.
        // AppModel therefore never sees an uncorrected terminal transcript followed by a second
        // corrected terminal transcript, which could otherwise execute two different actions.
        guard inputMode == .externalGlassesPCM, !next.isRunning else {
            snapshot = next
            return
        }

        guard terminalRepairTask == nil else { return }
        terminalRepairTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let repaired = await LocalAssistantSemanticRepair.shared.repair(next.transcript)
            guard !Task.isCancelled else { return }
            snapshot = SpeechTranscriptionSnapshot(
                transcript: repaired,
                isRunning: false,
                engineName: next.engineName
            )
            inputMode = nil
            terminalRepairTask = nil
        }
    }

    private func finishPendingTerminalRepair() async {
        let task = terminalRepairTask
        await task?.value
    }
}

/// Small, safety-bounded use of Qwen3 0.6B. The model does not answer questions and never emits BLE
/// bytes. It can only map a short, command-like, potentially misrecognized glasses transcript onto
/// one of a few already-verified product actions. A deterministic policy still decides whether the
/// model's suggestion is allowed to alter the transcript.
private actor LocalAssistantSemanticRepair {
    static let shared = LocalAssistantSemanticRepair()

#if canImport(CoreAILanguageModels) && !targetEnvironment(simulator)
    private var session: LanguageModelSession?
    private var loadAttempted = false
#endif

    func prewarm() async {
#if canImport(CoreAILanguageModels) && !targetEnvironment(simulator)
        _ = try? await languageSession()
#endif
    }

    func repair(_ rawTranscript: String) async -> String {
        let original = rawTranscript.trimmingCharacters(in: .whitespacesAndNewlines)
        guard SemanticCommandRepairPolicy.isCandidate(original) else { return original }

#if canImport(CoreAILanguageModels) && !targetEnvironment(simulator)
        if let decision = try? await classify(original),
           let canonical = SemanticCommandRepairPolicy.canonicalTranscript(
               for: decision.label,
               confidence: decision.confidence,
               original: original
           ) {
            return canonical
        }
#endif

        // This tiny fallback is intentional: it covers the known acoustic failure mode that
        // motivated semantic repair (for example "cling" for "click") even on a simulator or a
        // development phone where the large Core AI asset has not been staged yet. It is not a
        // general fuzzy-command engine.
        return SemanticCommandRepairPolicy.conservativeFallback(original) ?? original
    }

#if canImport(CoreAILanguageModels) && !targetEnvironment(simulator)
    private struct Decision {
        let label: String
        let confidence: Double
    }

    private func classify(_ transcript: String) async throws -> Decision? {
        guard let session = try await languageSession() else { return nil }
        let prompt = """
        Classify a short speech-recognition transcript from AD Glasses.
        The microphone may have misheard one word. You are NOT the assistant and must not answer it.

        Allowed labels only:
        CLICK_PHOTO = the user clearly intended the existing one-word photo command "click"
        START_VIDEO = the user clearly intended to start video recording
        START_AUDIO = the user clearly intended to start audio recording
        READ_TEXT = the user clearly intended to read or scan visible text
        NONE = ordinary conversation, a question, a negated command, or anything uncertain

        Be conservative. Never invent a capability or infer a command merely because it is listed.
        Return exactly LABEL|CONFIDENCE where CONFIDENCE is a decimal from 0.00 to 1.00.

        Transcript: \(transcript)
        """
        let response = try await session.respond(to: prompt)
        return Self.parseDecision(response.content)
    }

    private func languageSession() async throws -> LanguageModelSession? {
        if let session { return session }
        if loadAttempted { return nil }
        loadAttempted = true

        let modelURL = Self.modelURL
        guard FileManager.default.fileExists(atPath: modelURL.path) else { return nil }
        let model = try await CoreAILanguageModel(resourcesAt: modelURL)
        let session = LanguageModelSession(
            model: model,
            instructions: "You are a conservative intent classifier. Output only the requested label and confidence."
        )
        self.session = session
        return session
    }

    private static var modelURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("CoreAIModels", isDirectory: true)
            .appendingPathComponent("ADQwen3_0_6B_iOS", isDirectory: true)
    }

    private static func parseDecision(_ raw: String) -> Decision? {
        let cleaned = raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "`", with: "")
            .replacingOccurrences(of: "\"", with: "")
        let line = cleaned.split(whereSeparator: { $0.isNewline }).first.map(String.init) ?? cleaned
        let parts = line.split(separator: "|", maxSplits: 1).map {
            $0.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        guard parts.count == 2,
              let confidence = Double(parts[1]),
              (0...1).contains(confidence) else { return nil }
        let label = parts[0].uppercased()
        guard ["CLICK_PHOTO", "START_VIDEO", "START_AUDIO", "READ_TEXT", "NONE"].contains(label) else {
            return nil
        }
        return Decision(label: label, confidence: confidence)
    }
#endif
}

/// The model is advisory. This deterministic gate is the authority that decides whether a proposed
/// repair can become a real command. In particular, no stop-recording command is ever synthesized.
private enum SemanticCommandRepairPolicy {
    private static let questionStarters: Set<String> = [
        "what", "why", "how", "who", "where", "when", "which", "can", "could", "would",
        "should", "tell", "explain"
    ]
    private static let negations: Set<String> = ["not", "dont", "never", "no"]

    static func isCandidate(_ text: String) -> Bool {
        let words = normalizedWords(text)
        guard !words.isEmpty, words.count <= 10, text.count <= 96 else { return false }
        guard questionStarters.isDisjoint(with: words), negations.isDisjoint(with: words) else { return false }

        let anchors = [
            "click", "photo", "picture", "snap", "capture",
            "start", "begin", "record", "video", "audio",
            "read", "scan", "text", "sign"
        ]
        return words.contains { word in anchors.contains { editDistance(word, $0) <= 2 } }
    }

    static func canonicalTranscript(
        for label: String,
        confidence: Double,
        original: String
    ) -> String? {
        let words = normalizedWords(original)
        guard !words.isEmpty, negations.isDisjoint(with: words), questionStarters.isDisjoint(with: words) else {
            return nil
        }

        switch label {
        case "CLICK_PHOTO":
            guard confidence >= 0.94,
                  words.count <= 4,
                  hasNear(words, anyOf: ["click", "photo", "picture", "snap", "capture"]) else { return nil }
            return "click"

        case "START_VIDEO":
            guard confidence >= 0.94,
                  hasNear(words, anyOf: ["start", "begin", "record"]),
                  hasNear(words, anyOf: ["video"]) else { return nil }
            return "start video"

        case "START_AUDIO":
            guard confidence >= 0.94,
                  hasNear(words, anyOf: ["start", "begin", "record"]),
                  hasNear(words, anyOf: ["audio"]) else { return nil }
            return "start audio"

        case "READ_TEXT":
            guard confidence >= 0.92,
                  hasNear(words, anyOf: ["read", "scan"]),
                  hasNear(words, anyOf: ["text", "sign"]) else { return nil }
            return "read this text"

        default:
            return nil
        }
    }

    static func conservativeFallback(_ text: String) -> String? {
        let words = normalizedWords(text)
        guard words.count == 1 else { return nil }
        // Explicit, observed/plausible acoustic aliases only. Do not expand this into generic
        // Levenshtein command execution; Qwen plus the policy above owns broader repair.
        if ["cling", "clik", "clic", "clique"].contains(words[0]) {
            return "click"
        }
        return nil
    }

    private static func normalizedWords(_ text: String) -> [String] {
        text.lowercased()
            .replacingOccurrences(of: "’", with: "'")
            .replacingOccurrences(of: "'", with: "")
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
    }

    private static func hasNear(_ words: [String], anyOf anchors: [String]) -> Bool {
        words.contains { word in
            anchors.contains { anchor in editDistance(word, anchor) <= 2 }
        }
    }

    private static func editDistance(_ lhs: String, _ rhs: String) -> Int {
        let a = Array(lhs)
        let b = Array(rhs)
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }

        var previous = Array(0...b.count)
        for (i, left) in a.enumerated() {
            var current = [i + 1] + Array(repeating: 0, count: b.count)
            for (j, right) in b.enumerated() {
                let substitution = previous[j] + (left == right ? 0 : 1)
                let insertion = current[j] + 1
                let deletion = previous[j + 1] + 1
                current[j + 1] = Swift.min(substitution, Swift.min(insertion, deletion))
            }
            previous = current
        }
        return previous[b.count]
    }
}
