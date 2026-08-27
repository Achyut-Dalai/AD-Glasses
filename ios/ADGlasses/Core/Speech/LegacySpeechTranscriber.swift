import AVFoundation
import Foundation
import Speech

@MainActor
final class LegacySpeechTranscriber: SpeechTranscribing {
    let engineName = "Apple Speech"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    private(set) var snapshot: SpeechTranscriptionSnapshot {
        didSet { onUpdate?(snapshot) }
    }

    private let locale: Locale
    private let audioEngine = AVAudioEngine()
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?

    init(locale: Locale) {
        self.locale = locale
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: engineName
        )
    }

    func start() async throws {
        if snapshot.isRunning { return }
        try await SpeechPermissions.requestAll()

        guard let recognizer = SFSpeechRecognizer(locale: locale), recognizer.isAvailable else {
            throw SpeechTranscriptionError.recognizerUnavailable
        }

        await stop()

        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .spokenAudio, options: [.duckOthers, .allowBluetooth])
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.requiresOnDeviceRecognition = recognizer.supportsOnDeviceRecognition
        recognitionRequest = request

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        guard recordingFormat.sampleRate > 0 else {
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: recordingFormat) { [weak request] buffer, _ in
            request?.append(buffer)
        }

        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                if let result {
                    snapshot.transcript = result.bestTranscription.formattedString
                }
                if let error {
                    onError?(error)
                }
            }
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            snapshot.isRunning = true
        } catch {
            inputNode.removeTap(onBus: 0)
            recognitionRequest = nil
            recognitionTask = nil
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
            throw error
        }
    }

    func stop() async {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        recognitionRequest?.endAudio()
        recognitionTask?.finish()
        recognitionRequest = nil
        recognitionTask = nil
        snapshot.isRunning = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func resetTranscript() {
        snapshot.transcript = ""
    }
}
