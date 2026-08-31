@preconcurrency import AVFoundation
import Foundation
import Speech

@MainActor
final class LegacySpeechTranscriber: ExternalAudioSpeechTranscribing {
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
    private var inputSource: InputSource?
    private var externalRecognitionDidFinish = false
    private var externalFinishContinuation: CheckedContinuation<Void, Never>?
    private var externalFinishTimeoutTask: Task<Void, Never>?

    private enum InputSource {
        case phoneMicrophone
        case externalPCM
    }

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
        await stop()

        try prepareRecognition()

        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .spokenAudio, options: [.duckOthers, .allowBluetoothHFP])
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        guard recordingFormat.sampleRate > 0 else {
            clearRecognition()
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }
        guard let request = recognitionRequest else {
            clearRecognition()
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
            throw SpeechTranscriptionError.recognizerUnavailable
        }

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: recordingFormat) { [weak request] buffer, _ in
            request?.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            inputSource = .phoneMicrophone
            snapshot.isRunning = true
        } catch {
            inputNode.removeTap(onBus: 0)
            clearRecognition()
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
            throw error
        }
    }

    func stop() async {
        if inputSource == .externalPCM {
            await finishExternalAudio()
            return
        }
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        recognitionRequest?.endAudio()
        recognitionTask?.finish()
        clearRecognition()
        inputSource = nil
        snapshot.isRunning = false
        VoiceAudioSessionContinuity.shared.deactivateIfAllowed()
    }

    func startExternalAudio() async throws {
        if snapshot.isRunning { await stop() }
        try await SpeechPermissions.requestRecognition()
        try prepareRecognition()
        inputSource = .externalPCM
        snapshot.isRunning = true
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        guard inputSource == .externalPCM else { return }
        recognitionRequest?.append(buffer)
    }

    func finishExternalAudio() async {
        guard inputSource == .externalPCM else { return }
        recognitionRequest?.endAudio()
        recognitionTask?.finish()
        await waitForExternalRecognitionToFinish()
        clearRecognition()
        inputSource = nil
        snapshot.isRunning = false
    }

    func resetTranscript() {
        snapshot.transcript = ""
    }

    private func prepareRecognition() throws {
        guard let recognizer = SFSpeechRecognizer(locale: locale), recognizer.isAvailable else {
            throw SpeechTranscriptionError.recognizerUnavailable
        }

        externalRecognitionDidFinish = false
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.requiresOnDeviceRecognition = recognizer.supportsOnDeviceRecognition
        recognitionRequest = request

        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                if let result {
                    self.snapshot.transcript = result.bestTranscription.formattedString
                    if result.isFinal {
                        self.externalRecognitionDidFinish = true
                        self.completeExternalFinishWait()
                    }
                }
                if let error {
                    self.onError?(error)
                    self.externalRecognitionDidFinish = true
                    self.completeExternalFinishWait()
                }
            }
        }
    }

    private func waitForExternalRecognitionToFinish() async {
        guard recognitionTask != nil, !externalRecognitionDidFinish else { return }
        await withCheckedContinuation { continuation in
            externalFinishContinuation = continuation
            externalFinishTimeoutTask?.cancel()
            externalFinishTimeoutTask = Task { [weak self] in
                do {
                    try await Task.sleep(for: .seconds(2))
                } catch {
                    return
                }
                self?.completeExternalFinishWait()
            }
        }
    }

    private func completeExternalFinishWait() {
        externalFinishTimeoutTask?.cancel()
        externalFinishTimeoutTask = nil
        let continuation = externalFinishContinuation
        externalFinishContinuation = nil
        continuation?.resume()
    }

    private func clearRecognition() {
        completeExternalFinishWait()
        recognitionRequest = nil
        recognitionTask = nil
        externalRecognitionDidFinish = false
    }
}
