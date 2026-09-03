@preconcurrency import AVFoundation
import Combine
import Foundation

struct TextTranslationResult: Equatable, Sendable {
    let sourceText: String
    let translatedText: String
    let sourceLanguage: String
    let targetLanguage: String
}

enum TextTranslationError: LocalizedError, Sendable {
    case hostUnavailable
    case operationInProgress
    case sourceLanguageUndetermined
    case unsupportedLanguagePair
    case translationFailed(String)

    var errorDescription: String? {
        switch self {
        case .hostUnavailable:
            return "Apple Translation is not ready yet. Open AD Glasses and try again."
        case .operationInProgress:
            return "Another translation is already in progress."
        case .sourceLanguageUndetermined:
            return "Apple Translation could not identify the source language. Try a slightly longer phrase."
        case .unsupportedLanguagePair:
            return "Apple Translation does not support this source and target language combination."
        case .translationFailed(let reason):
            return "Apple Translation could not complete this request: \(reason)"
        }
    }
}

/// App-wide bridge used by deterministic AD shortcuts. NativeTranslationHost installs the closures
/// while Apple's translationTask is alive, keeping Assistant routing independent of SwiftUI view
/// lifetime while the app itself now targets iOS 27 and uses Translation directly.
@MainActor
final class AssistantTranslationBridge {
    static let shared = AssistantTranslationBridge()

    typealias TranslateHandler = @MainActor (
        _ text: String,
        _ sourceLanguageCode: String?,
        _ targetLanguageCode: String
    ) async throws -> TextTranslationResult

    typealias StartLiveHandler = @MainActor (
        _ sourceLanguageCode: String,
        _ targetLanguageCode: String,
        _ speechOutput: SpeechOutputController
    ) async -> Bool

    private(set) var translateHandler: TranslateHandler?
    private(set) var startLiveHandler: StartLiveHandler?
    private(set) var stopLiveHandler: (@MainActor () async -> Void)?
    private(set) var liveStateHandler: (@MainActor () -> Bool)?

    private init() {}

    func install(
        translate: @escaping TranslateHandler,
        startLive: @escaping StartLiveHandler,
        stopLive: @escaping @MainActor () async -> Void,
        isLiveRunning: @escaping @MainActor () -> Bool
    ) {
        translateHandler = translate
        startLiveHandler = startLive
        stopLiveHandler = stopLive
        liveStateHandler = isLiveRunning
    }

    func clear() {
        translateHandler = nil
        startLiveHandler = nil
        stopLiveHandler = nil
        liveStateHandler = nil
    }

    var isAvailable: Bool { translateHandler != nil }
    var isLiveRunning: Bool { liveStateHandler?() ?? false }

    func translate(
        _ text: String,
        sourceLanguageCode: String? = nil,
        targetLanguageCode: String
    ) async throws -> TextTranslationResult {
        guard let translateHandler else { throw TextTranslationError.hostUnavailable }
        return try await translateHandler(text, sourceLanguageCode, targetLanguageCode)
    }

    func startLive(
        sourceLanguageCode: String,
        targetLanguageCode: String,
        speechOutput: SpeechOutputController
    ) async -> Bool {
        guard let startLiveHandler else { return false }
        return await startLiveHandler(sourceLanguageCode, targetLanguageCode, speechOutput)
    }

    func stopLive() async {
        await stopLiveHandler?()
    }
}

// MARK: - Groq speech-to-English translation

enum LiveTranslationEngine: String, CaseIterable, Identifiable, Sendable {
    case groq
    case apple

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .groq: return "Groq Whisper"
        case .apple: return "Apple Offline"
        }
    }
}

enum GroqWhisperModel: String, CaseIterable, Identifiable, Sendable {
    case largeV3 = "whisper-large-v3"

    var id: String { rawValue }
    var displayName: String { "Whisper Large V3" }
    var detail: String { "Multilingual recognition with cloud text translation and Whisper audio fallback." }
}

enum GroqSpeechTranslationError: LocalizedError, Sendable {
    case missingCredential
    case microphoneUnavailable
    case recordingFailed
    case invalidResponse
    case requestFailed(String)

    var errorDescription: String? {
        switch self {
        case .missingCredential:
            return "Add a Groq Cloud AI profile and API key in Settings first."
        case .microphoneUnavailable:
            return "AD could not open the microphone for Live Translation."
        case .recordingFailed:
            return "AD could not record this translation turn."
        case .invalidResponse:
            return "Groq returned a translation response AD could not read."
        case .requestFailed(let reason):
            return reason
        }
    }
}

struct GroqSpeechSegment: Sendable {
    let averageLogProbability: Double?
    let noSpeechProbability: Double?
}

struct GroqSpeechResult: Sendable {
    let text: String
    let language: String?
    let segments: [GroqSpeechSegment]

    /// App-side guard against Whisper hallucinating a phrase from silence/background noise.
    var containsCredibleSpeech: Bool {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        guard !segments.isEmpty else { return true }

        return segments.contains { segment in
            let speechProbabilityIsCredible = segment.noSpeechProbability.map { $0 < 0.65 } ?? true
            let recognitionIsCredible = segment.averageLogProbability.map { $0 > -1.2 } ?? true
            return speechProbabilityIsCredible && recognitionIsCredible
        }
    }
}

struct LiveTranslationTurn: Identifiable, Equatable, Sendable {
    let id: UUID
    let sourceText: String
    let englishText: String
    let createdAt: Date

    init(sourceText: String, englishText: String, createdAt: Date = Date()) {
        id = UUID()
        self.sourceText = sourceText
        self.englishText = englishText
        self.createdAt = createdAt
    }
}

struct GroqAudioTranslationClient: Sendable {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func translateToEnglish(
        audioURL: URL,
        credential: String,
        model: GroqWhisperModel = .largeV3
    ) async throws -> GroqSpeechResult {
        try await request(
            path: "/audio/translations",
            audioURL: audioURL,
            credential: credential,
            model: model,
            language: nil
        )
    }

    func transcribe(
        audioURL: URL,
        credential: String,
        model: GroqWhisperModel = .largeV3,
        language: String? = nil
    ) async throws -> GroqSpeechResult {
        try await request(
            path: "/audio/transcriptions",
            audioURL: audioURL,
            credential: credential,
            model: model,
            language: language
        )
    }

    private func request(
        path: String,
        audioURL: URL,
        credential: String,
        model: GroqWhisperModel,
        language: String?
    ) async throws -> GroqSpeechResult {
        let key = credential.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { throw GroqSpeechTranslationError.missingCredential }
        guard let url = URL(string: "https://api.groq.com/openai/v1" + path) else {
            throw GroqSpeechTranslationError.invalidResponse
        }

        let audioData = try Data(contentsOf: audioURL, options: .mappedIfSafe)
        guard !audioData.isEmpty else { throw GroqSpeechTranslationError.recordingFailed }

        let boundary = "ADGlasses-\(UUID().uuidString)"
        var body = Data()
        body.appendMultipartField(name: "model", value: model.rawValue, boundary: boundary)
        body.appendMultipartField(name: "response_format", value: "verbose_json", boundary: boundary)
        body.appendMultipartField(name: "temperature", value: "0", boundary: boundary)
        if let language, !language.isEmpty {
            body.appendMultipartField(name: "language", value: language, boundary: boundary)
        }
        body.appendMultipartFile(
            name: "file",
            filename: "ad-live-translation.m4a",
            mimeType: "audio/mp4",
            data: audioData,
            boundary: boundary
        )
        body.appendString("--\(boundary)--\r\n")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 35
        request.httpBody = body
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard let http = response as? HTTPURLResponse else {
            throw GroqSpeechTranslationError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            throw GroqSpeechTranslationError.requestFailed(
                Self.errorMessage(from: data, statusCode: http.statusCode)
            )
        }
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let text = (root["text"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty else {
            throw GroqSpeechTranslationError.invalidResponse
        }

        let segments = (root["segments"] as? [[String: Any]] ?? []).map { segment in
            GroqSpeechSegment(
                averageLogProbability: Self.doubleValue(segment["avg_logprob"]),
                noSpeechProbability: Self.doubleValue(segment["no_speech_prob"])
            )
        }
        return GroqSpeechResult(
            text: text,
            language: root["language"] as? String,
            segments: segments
        )
    }

    private static func doubleValue(_ value: Any?) -> Double? {
        if let number = value as? NSNumber { return number.doubleValue }
        if let value = value as? Double { return value }
        return nil
    }

    fileprivate static func errorMessage(from data: Data, statusCode: Int) -> String {
        if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let error = root["error"] as? [String: Any],
           let message = error["message"] as? String,
           !message.isEmpty {
            return "Groq request failed: \(message)"
        }
        if statusCode == 401 || statusCode == 403 {
            return "Groq rejected the API key used for Live Translation."
        }
        if statusCode == 429 {
            return "Groq Live Translation hit its current rate limit. Try again shortly."
        }
        return "Groq request failed with HTTP \(statusCode)."
    }
}

/// Uses the user's configured Groq conversational model as a strict text translator. Whisper's
/// transcription is the source of truth, so short words and phrases do not depend on a second
/// audio-recognition pass to be translated correctly.
struct GroqTextTranslationClient: Sendable {
    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func translateToEnglish(
        text: String,
        sourceLanguageCode: String?,
        detectedLanguageCode: String?,
        model: String,
        credential: String
    ) async throws -> String {
        let source = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = credential.trimmingCharacters(in: .whitespacesAndNewlines)
        let model = model.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !source.isEmpty, !key.isEmpty, !model.isEmpty else {
            throw GroqSpeechTranslationError.invalidResponse
        }
        guard let url = URL(string: "https://api.groq.com/openai/v1/chat/completions") else {
            throw GroqSpeechTranslationError.invalidResponse
        }

        let languageHint = sourceLanguageCode ?? detectedLanguageCode ?? "auto-detected"
        let system = """
        You are a translation engine. Translate the user's source text into natural English and return only the English translation. Translate the semantic meaning even when the input is only one word or two words. Do not answer the text, explain it, transliterate it instead of translating it, add labels, or repeat non-English source text unchanged. Preserve proper names, numbers, and punctuation when appropriate. If the source is already English, return it unchanged.
        """
        let user = "Source language: \(languageHint)\nSource text:\n\(source)"

        var payload: [String: Any] = [
            "model": model,
            "messages": [
                ["role": "system", "content": system],
                ["role": "user", "content": user]
            ],
            "temperature": 0,
            "max_completion_tokens": 256
        ]
        let lowerModel = model.lowercased()
        if lowerModel.contains("gpt-oss") {
            payload["reasoning_effort"] = "low"
            payload["include_reasoning"] = false
        } else if lowerModel.contains("qwen3.6") || lowerModel.contains("qwen-3.6") {
            payload["reasoning_effort"] = "none"
            payload["reasoning_format"] = "hidden"
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard let http = response as? HTTPURLResponse else {
            throw GroqSpeechTranslationError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else {
            throw GroqSpeechTranslationError.requestFailed(
                GroqAudioTranslationClient.errorMessage(from: data, statusCode: http.statusCode)
            )
        }
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = root["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any],
              let translated = Self.messageText(message["content"]),
              !translated.isEmpty else {
            throw GroqSpeechTranslationError.invalidResponse
        }
        return Self.cleanModelOutput(translated)
    }

    private static func messageText(_ value: Any?) -> String? {
        if let text = value as? String {
            return text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let parts = value as? [[String: Any]] {
            let text = parts.compactMap { part -> String? in
                if let value = part["text"] as? String { return value }
                if let text = part["text"] as? [String: Any] { return text["value"] as? String }
                return nil
            }.joined()
            return text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return nil
    }

    private static func cleanModelOutput(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("```") && value.hasSuffix("```") {
            value = String(value.dropFirst(3).dropLast(3))
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if value.count >= 2,
           ((value.first == "\"" && value.last == "\"") ||
            (value.first == "“" && value.last == "”")) {
            value.removeFirst()
            value.removeLast()
        }
        return value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

@MainActor
final class GroqLiveTranslationController: ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var statusMessage = "Ready"
    @Published private(set) var lastSourceText = ""
    @Published private(set) var lastTranslation = ""
    @Published private(set) var recentTurns: [LiveTranslationTurn] = []
    @Published private(set) var inputRouteName: String?
    @Published private(set) var errorMessage: String?

    private let audioClient = GroqAudioTranslationClient()
    private let textClient = GroqTextTranslationClient()
    private weak var speechOutput: SpeechOutputController?
    private var recorder: AVAudioRecorder?
    private var recorderURL: URL?
    private var monitorTask: Task<Void, Never>?
    private var model: GroqWhisperModel = .largeV3
    private var cloudModel = ""
    private var credential = ""
    private var sourceLanguageCode: String?
    private var heardSpeech = false
    private var candidateSpeechFrames = 0
    private var lastSpeechAt: Date?
    private var recordingStartedAt: Date?

    // Live Translation is intentionally half-duplex. One isolated phrase is recorded, the mic and
    // HFP session close, translation and TTS finish, and only then is a fresh recorder opened.
    private let speechPowerThreshold: Float = -48
    private let requiredSpeechFrames = 3
    private let endSilenceSeconds: TimeInterval = 1.25
    private let minimumTurnSeconds: TimeInterval = 0.8
    private let maximumTurnSeconds: TimeInterval = 14
    private let idleRecycleSeconds: TimeInterval = 25

    @discardableResult
    func start(
        model: GroqWhisperModel,
        cloudModel: String,
        credential: String,
        sourceLanguageCode: String? = nil,
        speechOutput: SpeechOutputController
    ) async -> Bool {
        if isRunning { return true }
        let key = credential.trimmingCharacters(in: .whitespacesAndNewlines)
        let cloudModel = cloudModel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, !cloudModel.isEmpty else {
            errorMessage = GroqSpeechTranslationError.missingCredential.localizedDescription
            return false
        }
        guard speechOutput.preferredVoice(languageCode: "en") != nil else {
            errorMessage = SpeechOutputError.noVoiceAvailable.localizedDescription
            return false
        }

        do {
            try await SpeechPermissions.requestAll()
            try await SpeechInputAudioSession.activate()
        } catch {
            errorMessage = error.localizedDescription
            return false
        }

        self.model = model
        self.cloudModel = cloudModel
        self.credential = key
        self.sourceLanguageCode = Self.normalizedLanguageCode(sourceLanguageCode)
        self.speechOutput = speechOutput
        errorMessage = nil
        lastSourceText = ""
        lastTranslation = ""
        recentTurns = []
        isRunning = true

        do {
            try startRecorder()
            statusMessage = listeningStatus
            startMonitor()
            return true
        } catch {
            errorMessage = error.localizedDescription
            await stop()
            return false
        }
    }

    func stop() async {
        isRunning = false
        monitorTask?.cancel()
        monitorTask = nil
        recorder?.stop()
        recorder = nil
        removeRecorderFile()
        heardSpeech = false
        candidateSpeechFrames = 0
        lastSpeechAt = nil
        recordingStartedAt = nil
        speechOutput?.stop()
        speechOutput = nil
        credential = ""
        cloudModel = ""
        sourceLanguageCode = nil
        inputRouteName = nil
        statusMessage = "Ready"
        await SpeechInputAudioSession.deactivate()
    }

    private func startRecorder() throws {
        guard isRunning else { return }
        removeRecorderFile()

        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("ADLiveTranslation", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent(UUID().uuidString).appendingPathExtension("m4a")
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 32_000,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue
        ]
        let recorder = try AVAudioRecorder(url: url, settings: settings)
        recorder.isMeteringEnabled = true
        recorder.prepareToRecord()
        guard recorder.record() else { throw GroqSpeechTranslationError.recordingFailed }

        self.recorder = recorder
        recorderURL = url
        heardSpeech = false
        candidateSpeechFrames = 0
        lastSpeechAt = nil
        recordingStartedAt = Date()
        inputRouteName = AVAudioSession.sharedInstance().currentRoute.inputs.first?.portName
            ?? "iPhone microphone"
    }

    private func startMonitor() {
        monitorTask?.cancel()
        monitorTask = Task { @MainActor [weak self] in
            guard let self else { return }
            while isRunning, !Task.isCancelled {
                do {
                    try await Task.sleep(for: .milliseconds(100))
                } catch {
                    return
                }
                guard let recorder, recorder.isRecording,
                      let started = recordingStartedAt else { continue }

                recorder.updateMeters()
                let now = Date()
                let elapsed = now.timeIntervalSince(started)
                let power = recorder.averagePower(forChannel: 0)

                if power >= speechPowerThreshold {
                    if heardSpeech {
                        lastSpeechAt = now
                    } else {
                        candidateSpeechFrames += 1
                        if candidateSpeechFrames >= requiredSpeechFrames {
                            heardSpeech = true
                            lastSpeechAt = now
                        }
                    }
                } else if !heardSpeech {
                    candidateSpeechFrames = 0
                }

                if heardSpeech {
                    let silence = lastSpeechAt.map { now.timeIntervalSince($0) } ?? 0
                    if (silence >= endSilenceSeconds && elapsed >= minimumTurnSeconds) ||
                        elapsed >= maximumTurnSeconds {
                        await finishCurrentTurn()
                    }
                } else if elapsed >= idleRecycleSeconds {
                    recorder.stop()
                    self.recorder = nil
                    removeRecorderFile()
                    do {
                        try startRecorder()
                    } catch {
                        errorMessage = error.localizedDescription
                        await stop()
                        return
                    }
                }
            }
        }
    }

    private func finishCurrentTurn() async {
        guard isRunning, let recorder, let audioURL = recorderURL else { return }
        recorder.stop()
        self.recorder = nil
        recorderURL = nil
        heardSpeech = false
        candidateSpeechFrames = 0
        lastSpeechAt = nil
        recordingStartedAt = nil

        // Recording is finished before any network request or speech begins. Releasing HFP here is
        // what prevents the spoken English result from becoming the next microphone turn.
        await SpeechInputAudioSession.deactivate()

        defer { try? FileManager.default.removeItem(at: audioURL) }
        let fileSize = (try? audioURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard fileSize > 1_500 else {
            await resumeListening()
            return
        }

        errorMessage = nil
        statusMessage = "Whisper Large V3 transcribing…"

        do {
            let transcription = try await audioClient.transcribe(
                audioURL: audioURL,
                credential: credential,
                model: model,
                language: sourceLanguageCode
            )
            try Task.checkCancellation()
            guard isRunning else { return }

            guard transcription.containsCredibleSpeech else {
                statusMessage = "Noise ignored"
                await resumeListening()
                return
            }

            let sourceText = transcription.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !sourceText.isEmpty else {
                await resumeListening()
                return
            }
            lastSourceText = sourceText
            lastTranslation = ""

            let english = try await translateRecognizedText(
                sourceText,
                transcription: transcription,
                audioURL: audioURL
            )
            try Task.checkCancellation()
            guard isRunning else { return }

            let cleanEnglish = english.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleanEnglish.isEmpty else { throw GroqSpeechTranslationError.invalidResponse }

            lastTranslation = cleanEnglish
            recentTurns.insert(
                LiveTranslationTurn(sourceText: sourceText, englishText: cleanEnglish),
                at: 0
            )
            if recentTurns.count > 12 {
                recentTurns.removeLast(recentTurns.count - 12)
            }

            if let speechOutput {
                statusMessage = "Speaking English…"
                try speechOutput.speak(
                    cleanEnglish,
                    languageCode: "en",
                    audioSessionPolicy: .managedPlayback
                )
                while speechOutput.isSpeaking {
                    try Task.checkCancellation()
                    try await Task.sleep(for: .milliseconds(80))
                }
            }

            await resumeListening()
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
            await resumeListening()
        }
    }

    private func translateRecognizedText(
        _ sourceText: String,
        transcription: GroqSpeechResult,
        audioURL: URL
    ) async throws -> String {
        let recognizedLanguage = sourceLanguageCode ?? Self.normalizedLanguageCode(transcription.language)
        if recognizedLanguage.map(Self.languageBase) == "en" {
            return sourceText
        }

        var cloudFailure: Error?
        statusMessage = "Groq AI translating to English…"
        do {
            let translated = try await textClient.translateToEnglish(
                text: sourceText,
                sourceLanguageCode: sourceLanguageCode,
                detectedLanguageCode: transcription.language,
                model: cloudModel,
                credential: credential
            )
            if !Self.isSuspiciousUnchangedTranslation(
                source: sourceText,
                translated: translated,
                sourceLanguageCode: recognizedLanguage
            ) {
                return translated
            }
            cloudFailure = GroqSpeechTranslationError.requestFailed(
                "Groq AI returned the recognized source text without translating it."
            )
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            cloudFailure = error
        }

        // Keep Whisper's direct audio→English endpoint as a resilience fallback, not the primary
        // translator. It can recover if the user's configured chat model is temporarily unavailable.
        statusMessage = "Whisper translation fallback…"
        do {
            let translated = try await audioClient.translateToEnglish(
                audioURL: audioURL,
                credential: credential,
                model: .largeV3
            ).text
            guard !Self.isSuspiciousUnchangedTranslation(
                source: sourceText,
                translated: translated,
                sourceLanguageCode: recognizedLanguage
            ) else {
                throw GroqSpeechTranslationError.requestFailed(
                    "Groq recognized the speech correctly, but both translation paths returned the source text unchanged."
                )
            }
            return translated
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            if let cloudFailure {
                throw GroqSpeechTranslationError.requestFailed(
                    "Translation failed after a correct transcription. Cloud AI: \(cloudFailure.localizedDescription) Fallback: \(error.localizedDescription)"
                )
            }
            throw error
        }
    }

    private func resumeListening() async {
        guard isRunning, !Task.isCancelled else { return }
        do {
            try await SpeechInputAudioSession.activate()
            try startRecorder()
            statusMessage = listeningStatus
        } catch {
            errorMessage = error.localizedDescription
            await stop()
        }
    }

    private var listeningStatus: String {
        guard let sourceLanguageCode else {
            return "Listening — Auto Detect"
        }
        return "Listening — \(Self.languageName(sourceLanguageCode))"
    }

    private func removeRecorderFile() {
        guard let recorderURL else { return }
        try? FileManager.default.removeItem(at: recorderURL)
        self.recorderURL = nil
    }

    private static func isSuspiciousUnchangedTranslation(
        source: String,
        translated: String,
        sourceLanguageCode: String?
    ) -> Bool {
        guard sourceLanguageCode.map(languageBase) != "en" else { return false }
        guard sourceLanguageCode != nil else { return false }
        return normalizedComparisonText(source) == normalizedComparisonText(translated)
    }

    private static func normalizedComparisonText(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    private static func normalizedLanguageCode(_ code: String?) -> String? {
        guard let code else { return nil }
        let value = code.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return value.isEmpty ? nil : languageBase(value)
    }

    private static func languageBase(_ code: String) -> String {
        let normalized = code.replacingOccurrences(of: "_", with: "-").lowercased()
        return normalized.split(separator: "-").first.map(String.init) ?? normalized
    }

    private static func languageName(_ code: String) -> String {
        Locale.current.localizedString(forLanguageCode: languageBase(code)) ?? code
    }
}

private extension Data {
    mutating func appendString(_ string: String) {
        append(Data(string.utf8))
    }

    mutating func appendMultipartField(name: String, value: String, boundary: String) {
        appendString("--\(boundary)\r\n")
        appendString("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
        appendString(value)
        appendString("\r\n")
    }

    mutating func appendMultipartFile(
        name: String,
        filename: String,
        mimeType: String,
        data: Data,
        boundary: String
    ) {
        appendString("--\(boundary)\r\n")
        appendString("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n")
        appendString("Content-Type: \(mimeType)\r\n\r\n")
        append(data)
        appendString("\r\n")
    }
}
