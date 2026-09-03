@preconcurrency import AVFoundation
import Combine
import Foundation
import NaturalLanguage

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
    case largeV3Turbo = "whisper-large-v3-turbo"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .largeV3: return "Whisper Large V3"
        case .largeV3Turbo: return "Whisper Large V3 Turbo"
        }
    }

    var detail: String {
        switch self {
        case .largeV3:
            return "Direct multilingual audio → English translation. Best accuracy for difficult audio."
        case .largeV3Turbo:
            return "Fast multilingual transcription, then English translation. Falls back to Large V3 direct translation when needed."
        }
    }
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
            return "Groq returned a speech response AD could not read."
        case .requestFailed(let reason):
            return reason
        }
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
    ) async throws -> String {
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
        model: GroqWhisperModel,
        language: String? = nil
    ) async throws -> String {
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
    ) async throws -> String {
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
        body.appendMultipartField(name: "response_format", value: "json", boundary: boundary)
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
        return text
    }

    private static func errorMessage(from data: Data, statusCode: Int) -> String {
        if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let error = root["error"] as? [String: Any],
           let message = error["message"] as? String,
           !message.isEmpty {
            return "Groq speech request failed: \(message)"
        }
        if statusCode == 401 || statusCode == 403 {
            return "Groq rejected the API key used for Live Translation."
        }
        if statusCode == 429 {
            return "Groq Live Translation hit its current rate limit. Try again shortly or switch to Apple Offline."
        }
        return "Groq speech request failed with HTTP \(statusCode)."
    }
}

@MainActor
final class GroqLiveTranslationController: ObservableObject {
    @Published private(set) var isRunning = false
    @Published private(set) var statusMessage = "Ready"
    @Published private(set) var lastSourceText = ""
    @Published private(set) var lastTranslation = ""
    @Published private(set) var inputRouteName: String?
    @Published private(set) var errorMessage: String?

    private let client = GroqAudioTranslationClient()
    private weak var appleTranslation: NativeTranslationController?
    private weak var speechOutput: SpeechOutputController?
    private var recorder: AVAudioRecorder?
    private var recorderURL: URL?
    private var monitorTask: Task<Void, Never>?
    private var model: GroqWhisperModel = .largeV3
    private var credential = ""
    private var heardSpeech = false
    private var lastSpeechAt: Date?
    private var recordingStartedAt: Date?

    // Metering is deliberately forgiving because the glasses/HFP microphone can be quiet. The
    // maximum turn duration is the safety net in noisy environments where silence never settles.
    private let speechPowerThreshold: Float = -48
    private let endSilenceSeconds: TimeInterval = 1.0
    private let minimumTurnSeconds: TimeInterval = 0.55
    private let maximumTurnSeconds: TimeInterval = 14
    private let idleRecycleSeconds: TimeInterval = 25

    @discardableResult
    func start(
        model: GroqWhisperModel,
        credential: String,
        appleTranslation: NativeTranslationController,
        speechOutput: SpeechOutputController
    ) async -> Bool {
        if isRunning { return true }
        let key = credential.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            errorMessage = GroqSpeechTranslationError.missingCredential.localizedDescription
            return false
        }
        guard speechOutput.preferredVoice(languageCode: "en") != nil else {
            errorMessage = SpeechOutputError.noVoiceAvailable.localizedDescription
            return false
        }

        do {
            try await SpeechPermissions.requestAll()
            try SpeechInputAudioSession.activate()
        } catch {
            errorMessage = error.localizedDescription
            return false
        }

        self.model = model
        self.credential = key
        self.appleTranslation = appleTranslation
        self.speechOutput = speechOutput
        errorMessage = nil
        lastSourceText = ""
        lastTranslation = ""
        isRunning = true

        do {
            try startRecorder()
            statusMessage = "Listening — auto-detecting language"
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
        lastSpeechAt = nil
        recordingStartedAt = nil
        speechOutput?.stop()
        appleTranslation = nil
        speechOutput = nil
        credential = ""
        inputRouteName = nil
        statusMessage = "Ready"
        SpeechInputAudioSession.deactivate()
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
                    heardSpeech = true
                    lastSpeechAt = now
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
        lastSpeechAt = nil
        recordingStartedAt = nil

        defer { try? FileManager.default.removeItem(at: audioURL) }
        guard (try? audioURL.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0) ?? 0 > 1_500 else {
            await resumeRecording()
            return
        }

        statusMessage = model == .largeV3
            ? "Whisper Large V3 → English…"
            : "Whisper Turbo transcribing…"
        errorMessage = nil

        do {
            let english: String
            switch model {
            case .largeV3:
                lastSourceText = ""
                english = try await client.translateToEnglish(
                    audioURL: audioURL,
                    credential: credential,
                    model: .largeV3
                )

            case .largeV3Turbo:
                let sourceText = try await client.transcribe(
                    audioURL: audioURL,
                    credential: credential,
                    model: .largeV3Turbo
                )
                try Task.checkCancellation()
                lastSourceText = sourceText

                if Self.isLikelyEnglish(sourceText) {
                    english = sourceText
                } else if let appleTranslation {
                    do {
                        statusMessage = "Translating transcript to English…"
                        let result = try await appleTranslation.translate(
                            sourceText,
                            from: nil,
                            to: Locale.Language(identifier: "en")
                        )
                        english = result.translatedText
                    } catch {
                        // Groq's public capability documentation has historically differed between
                        // the generic API schema and its model capability table for Turbo translation.
                        // Do not depend on an ambiguous behavior: if text translation cannot finish,
                        // use the documented Large V3 direct audio→English path with the same audio.
                        statusMessage = "Using Large V3 translation fallback…"
                        english = try await client.translateToEnglish(
                            audioURL: audioURL,
                            credential: credential,
                            model: .largeV3
                        )
                    }
                } else {
                    english = try await client.translateToEnglish(
                        audioURL: audioURL,
                        credential: credential,
                        model: .largeV3
                    )
                }
            }

            try Task.checkCancellation()
            guard isRunning else { return }
            let cleanEnglish = english.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleanEnglish.isEmpty else { throw GroqSpeechTranslationError.invalidResponse }
            lastTranslation = cleanEnglish

            if let speechOutput {
                statusMessage = "Speaking English…"
                try SpeechInputAudioSession.activate()
                try speechOutput.speak(
                    cleanEnglish,
                    languageCode: "en",
                    audioSessionPolicy: .reuseCurrentSession
                )
                while speechOutput.isSpeaking {
                    try Task.checkCancellation()
                    try await Task.sleep(for: .milliseconds(80))
                }
            }
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }

        guard isRunning, !Task.isCancelled else { return }
        await resumeRecording()
    }

    private func resumeRecording() async {
        guard isRunning else { return }
        do {
            try SpeechInputAudioSession.activate()
            try startRecorder()
            statusMessage = "Listening — auto-detecting language"
        } catch {
            errorMessage = error.localizedDescription
            await stop()
        }
    }

    private func removeRecorderFile() {
        guard let recorderURL else { return }
        try? FileManager.default.removeItem(at: recorderURL)
        self.recorderURL = nil
    }

    private static func isLikelyEnglish(_ text: String) -> Bool {
        guard text.count >= 3 else { return false }
        let recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        let hypotheses = recognizer.languageHypotheses(withMaximum: 3)
        let english = hypotheses
            .filter { $0.key == .english }
            .map(\.value)
            .max() ?? 0
        return english >= 0.60
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
