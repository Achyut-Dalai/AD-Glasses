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

    var id: String { rawValue }
    var displayName: String { "Whisper Large V3" }
    var detail: String { "Accuracy-oriented multilingual transcription with English translation fallback." }
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

struct GroqSpeechSegment: Sendable {
    let averageLogProbability: Double?
    let noSpeechProbability: Double?
}

struct GroqSpeechResult: Sendable {
    let text: String
    let language: String?
    let segments: [GroqSpeechSegment]

    /// App-side guard against Whisper hallucinating a phrase from silence/background noise.
    /// Groq exposes these values as diagnostics rather than prescribing universal thresholds, so
    /// keep this deliberately conservative and let any credible segment admit the utterance.
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
    @Published private(set) var recentTurns: [LiveTranslationTurn] = []
    @Published private(set) var inputRouteName: String?
    @Published private(set) var errorMessage: String?

    private let client = GroqAudioTranslationClient()
    private weak var appleTranslation: NativeTranslationController?
    private weak var speechOutput: SpeechOutputController?
    private var recorder: AVAudioRecorder?
    private var recorderURL: URL?
    private var monitorTask: Task<Void, Never>?
    private var processingTask: Task<Void, Never>?
    private var pendingAudioURLs: [URL] = []
    private var model: GroqWhisperModel = .largeV3
    private var credential = ""
    private var sourceLanguageCode: String?
    private var heardSpeech = false
    private var candidateSpeechFrames = 0
    private var bargeInFrames = 0
    private var bargeInDetectedForCurrentCapture = false
    private var lastSpeechAt: Date?
    private var recordingStartedAt: Date?
    private var lastSpokenEnglish = ""

    // Normal VAD remains forgiving for quiet HFP microphones. While AD itself is speaking, a much
    // stronger sustained threshold is required before we consider the input a real barge-in; this
    // prevents our own TTS leakage from recursively becoming a new Whisper request.
    private let speechPowerThreshold: Float = -48
    private let requiredSpeechFrames = 3
    private let bargeInPowerThreshold: Float = -36
    private let requiredBargeInFrames = 4
    private let endSilenceSeconds: TimeInterval = 1.25
    private let minimumTurnSeconds: TimeInterval = 0.8
    private let maximumTurnSeconds: TimeInterval = 14
    private let idleRecycleSeconds: TimeInterval = 25
    private let maximumQueuedTurns = 8

    @discardableResult
    func start(
        model: GroqWhisperModel,
        credential: String,
        sourceLanguageCode: String? = nil,
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
        self.sourceLanguageCode = Self.normalizedLanguageCode(sourceLanguageCode)
        self.appleTranslation = appleTranslation
        self.speechOutput = speechOutput
        errorMessage = nil
        lastSourceText = ""
        lastTranslation = ""
        recentTurns = []
        pendingAudioURLs.removeAll()
        lastSpokenEnglish = ""
        isRunning = true

        do {
            try startRecorder()
            speechOutput.refreshOutputRouteName()
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
        processingTask?.cancel()
        processingTask = nil
        recorder?.stop()
        recorder = nil
        removeRecorderFile()
        for url in pendingAudioURLs {
            try? FileManager.default.removeItem(at: url)
        }
        pendingAudioURLs.removeAll()
        heardSpeech = false
        candidateSpeechFrames = 0
        bargeInFrames = 0
        bargeInDetectedForCurrentCapture = false
        lastSpeechAt = nil
        recordingStartedAt = nil
        speechOutput?.stop()
        appleTranslation = nil
        speechOutput = nil
        credential = ""
        sourceLanguageCode = nil
        lastSpokenEnglish = ""
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
        candidateSpeechFrames = 0
        bargeInFrames = 0
        bargeInDetectedForCurrentCapture = false
        lastSpeechAt = nil
        recordingStartedAt = Date()
        inputRouteName = AVAudioSession.sharedInstance().currentRoute.inputs.first?.portName
            ?? "iPhone microphone"
        speechOutput?.refreshOutputRouteName()
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
                let adIsSpeaking = speechOutput?.isSpeaking ?? false

                if adIsSpeaking, !heardSpeech {
                    // Don't let our own playback enter ordinary VAD. A real interruption must be
                    // distinctly louder and sustained; once accepted, stop TTS immediately so the
                    // other person owns the conversational floor.
                    if power >= bargeInPowerThreshold {
                        bargeInFrames += 1
                        if bargeInFrames >= requiredBargeInFrames {
                            bargeInDetectedForCurrentCapture = true
                            heardSpeech = true
                            candidateSpeechFrames = 0
                            lastSpeechAt = now
                            speechOutput?.stop()
                            statusMessage = "Barge-in detected · listening"
                        }
                    } else {
                        bargeInFrames = 0
                    }
                } else {
                    bargeInFrames = 0
                    if power >= speechPowerThreshold {
                        if heardSpeech {
                            lastSpeechAt = now
                            if adIsSpeaking, power >= bargeInPowerThreshold {
                                bargeInDetectedForCurrentCapture = true
                                speechOutput?.stop()
                            }
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
                }

                if heardSpeech {
                    let silence = lastSpeechAt.map { now.timeIntervalSince($0) } ?? 0
                    if (silence >= endSilenceSeconds && elapsed >= minimumTurnSeconds) ||
                        elapsed >= maximumTurnSeconds {
                        await finishCaptureTurn()
                    }
                } else if elapsed >= idleRecycleSeconds, !adIsSpeaking {
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

    /// Close only the finished capture and immediately open the next recorder. Network inference,
    /// Apple Translation, and TTS happen on a separate serial processing task, so nearby speech is
    /// still capturable while the previous phrase is being translated or spoken.
    private func finishCaptureTurn() async {
        guard isRunning, let recorder, let audioURL = recorderURL else { return }
        recorder.stop()
        self.recorder = nil
        recorderURL = nil
        heardSpeech = false
        candidateSpeechFrames = 0
        bargeInFrames = 0
        bargeInDetectedForCurrentCapture = false
        lastSpeechAt = nil
        recordingStartedAt = nil

        let fileSize = (try? audioURL.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0

        do {
            try startRecorder()
        } catch {
            try? FileManager.default.removeItem(at: audioURL)
            errorMessage = error.localizedDescription
            await stop()
            return
        }

        guard fileSize > 1_500 else {
            try? FileManager.default.removeItem(at: audioURL)
            return
        }

        if pendingAudioURLs.count >= maximumQueuedTurns {
            // Keep the newest speech rather than letting a stalled network create an unbounded
            // memory/disk queue. The discarded turn is explicit in status instead of being mixed
            // with another audio file.
            let dropped = pendingAudioURLs.removeFirst()
            try? FileManager.default.removeItem(at: dropped)
            errorMessage = "Live Translation fell behind and skipped one older phrase."
        }
        pendingAudioURLs.append(audioURL)
        statusMessage = pendingAudioURLs.count > 1
            ? "Listening · \(pendingAudioURLs.count) phrases queued"
            : "Listening · translating previous phrase"
        startProcessingQueueIfNeeded()
    }

    private func startProcessingQueueIfNeeded() {
        guard processingTask == nil else { return }
        processingTask = Task { @MainActor [weak self] in
            guard let self else { return }
            while isRunning, !Task.isCancelled, !pendingAudioURLs.isEmpty {
                let audioURL = pendingAudioURLs.removeFirst()
                await processCapturedTurn(audioURL)
            }
            processingTask = nil
            if isRunning, !Task.isCancelled {
                statusMessage = listeningStatus
            }
        }
    }

    private func processCapturedTurn(_ audioURL: URL) async {
        defer { try? FileManager.default.removeItem(at: audioURL) }
        statusMessage = "Whisper Large V3 transcribing · still listening"
        errorMessage = nil

        do {
            let transcription = try await client.transcribe(
                audioURL: audioURL,
                credential: credential,
                model: model,
                language: sourceLanguageCode
            )
            try Task.checkCancellation()
            guard isRunning else { return }

            // Do not turn silence into UI text, translation, or TTS. Every queued file represents
            // exactly one isolated capture; prior audio is never appended to this request.
            guard transcription.containsCredibleSpeech else {
                statusMessage = "Noise ignored · still listening"
                return
            }

            let sourceText = transcription.text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !sourceText.isEmpty else { return }

            // This is a secondary guard. The capture VAD already suppresses normal TTS while AD is
            // speaking, but Auto Detect can also recognize a residual Bluetooth acoustic echo as
            // English. Never feed an obvious copy of our immediately previous spoken result back in.
            if sourceLanguageCode == nil,
               Self.isLikelyEnglish(sourceText),
               Self.isLikelySelfEcho(sourceText, of: lastSpokenEnglish) {
                statusMessage = "AD speech echo ignored · still listening"
                return
            }

            lastSourceText = sourceText
            lastTranslation = ""

            let english: String
            if sourceLanguageCode.map(Self.languageBase) == "en" ||
                (sourceLanguageCode == nil && Self.isLikelyEnglish(sourceText)) {
                english = sourceText
            } else if let appleTranslation {
                do {
                    statusMessage = "Translating transcript to English · still listening"
                    let sourceLanguage = sourceLanguageCode.map { Locale.Language(identifier: $0) }
                    let result = try await appleTranslation.translate(
                        sourceText,
                        from: sourceLanguage,
                        to: Locale.Language(identifier: "en")
                    )
                    english = result.translatedText
                } catch is CancellationError {
                    throw CancellationError()
                } catch {
                    // Some Whisper languages are not available as an Apple Translation pair. Keep
                    // the accurate source transcript, then fall back to Large V3's documented
                    // direct audio→English translation path for the same isolated audio turn.
                    statusMessage = "Whisper translating to English · still listening"
                    english = try await client.translateToEnglish(
                        audioURL: audioURL,
                        credential: credential,
                        model: .largeV3
                    ).text
                }
            } else {
                english = try await client.translateToEnglish(
                    audioURL: audioURL,
                    credential: credential,
                    model: .largeV3
                ).text
            }

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
                lastSpokenEnglish = cleanEnglish
                statusMessage = "Speaking English · still listening"
                try SpeechInputAudioSession.activate()
                speechOutput.refreshOutputRouteName()
                try speechOutput.speak(
                    cleanEnglish,
                    languageCode: "en",
                    audioSessionPolicy: .reuseCurrentSession
                )
                while speechOutput.isSpeaking {
                    try Task.checkCancellation()
                    try await Task.sleep(for: .milliseconds(80))
                }

                // If nobody barged in while AD spoke, throw away the active file that contained
                // playback leakage and immediately start a clean capture. If real speech has begun,
                // preserve that file instead; the user always wins over cleanup.
                if isRunning,
                   !heardSpeech,
                   candidateSpeechFrames == 0,
                   !bargeInDetectedForCurrentCapture {
                    try resetRecorderAfterPlayback()
                }
            }
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func resetRecorderAfterPlayback() throws {
        guard isRunning else { return }
        let oldURL = recorderURL
        recorder?.stop()
        recorder = nil
        recorderURL = nil
        if let oldURL {
            try? FileManager.default.removeItem(at: oldURL)
        }
        try startRecorder()
        statusMessage = listeningStatus
    }

    private var listeningStatus: String {
        guard let sourceLanguageCode else {
            return "Listening — auto-detecting language"
        }
        return "Listening — \(Self.languageName(sourceLanguageCode))"
    }

    private func removeRecorderFile() {
        guard let recorderURL else { return }
        try? FileManager.default.removeItem(at: recorderURL)
        self.recorderURL = nil
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

    private static func isLikelySelfEcho(_ text: String, of spoken: String) -> Bool {
        let lhs = normalizedWords(text)
        let rhs = normalizedWords(spoken)
        guard !lhs.isEmpty, !rhs.isEmpty else { return false }
        if lhs == rhs { return true }

        let left = Set(lhs)
        let right = Set(rhs)
        let overlap = left.intersection(right).count
        let denominator = max(left.count, right.count)
        guard denominator > 0 else { return false }
        return Double(overlap) / Double(denominator) >= 0.78
    }

    private static func normalizedWords(_ value: String) -> [String] {
        value.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
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
