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
    private var endpointTask: Task<Void, Never>?
    private var lastEndpointTranscript = ""

    private let transcriptSilenceDelay: Duration = .milliseconds(1_200)
    private let initialNoSpeechDelay: Duration = .seconds(6)

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

        do {
            try SpeechInputAudioSession.activate()
        } catch {
            clearRecognition()
            throw error
        }

        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        guard recordingFormat.sampleRate > 0 else {
            clearRecognition()
            SpeechInputAudioSession.deactivate()
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }
        guard let request = recognitionRequest else {
            clearRecognition()
            SpeechInputAudioSession.deactivate()
            throw SpeechTranscriptionError.recognizerUnavailable
        }

        inputNode.installTap(onBus: 0, bufferSize: 1_024, format: recordingFormat) { [weak request] buffer, _ in
            request?.append(buffer)
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            inputSource = .phoneMicrophone
            snapshot.isRunning = true
            armInitialEndpoint()
        } catch {
            inputNode.removeTap(onBus: 0)
            clearRecognition()
            SpeechInputAudioSession.deactivate()
            throw error
        }
    }

    func stop() async {
        endpointTask?.cancel()
        endpointTask = nil
        lastEndpointTranscript = ""

        if inputSource == .externalPCM {
            await finishExternalAudio()
            return
        }

        let wasUsingMicrophone = inputSource == .phoneMicrophone
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        recognitionRequest?.endAudio()
        recognitionTask?.finish()
        await waitForExternalRecognitionToFinish()
        clearRecognition()
        inputSource = nil
        snapshot.isRunning = false
        if wasUsingMicrophone {
            SpeechInputAudioSession.deactivate()
        }
    }

    func startExternalAudio() async throws {
        if snapshot.isRunning { await stop() }
        try await SpeechPermissions.requestRecognition()
        try prepareRecognition()
        inputSource = .externalPCM
        snapshot.isRunning = true
        armInitialEndpoint()
    }

    func appendExternalAudio(_ buffer: AVAudioPCMBuffer) {
        guard inputSource == .externalPCM else { return }
        recognitionRequest?.append(buffer)
    }

    func finishExternalAudio() async {
        endpointTask?.cancel()
        endpointTask = nil
        lastEndpointTranscript = ""
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
        lastEndpointTranscript = ""
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
                    let text = result.bestTranscription.formattedString
                    self.snapshot.transcript = text
                    self.noteTranscriptActivity(text)
                    if result.isFinal {
                        self.externalRecognitionDidFinish = true
                        self.completeExternalFinishWait()
                    }
                }
                if let error {
                    self.onError?(error)
                    self.externalRecognitionDidFinish = true
                    self.completeExternalFinishWait()
                    if self.snapshot.isRunning {
                        Task { @MainActor [weak self] in
                            await self?.stop()
                        }
                    }
                }
            }
        }
    }

    /// Mirrors the Android Moonshine turn policy: after speech begins, 1.2 seconds without a
    /// transcript change ends capture; a completely silent turn ends after six seconds.
    private func armInitialEndpoint() {
        endpointTask?.cancel()
        endpointTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.initialNoSpeechDelay ?? .seconds(6))
            } catch {
                return
            }
            guard let self,
                  snapshot.isRunning,
                  snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return
            }
            await stop()
        }
    }

    private func noteTranscriptActivity(_ text: String) {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, clean != lastEndpointTranscript else { return }
        lastEndpointTranscript = clean
        endpointTask?.cancel()
        endpointTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: self?.transcriptSilenceDelay ?? .milliseconds(1_200))
            } catch {
                return
            }
            guard let self,
                  snapshot.isRunning,
                  snapshot.transcript.trimmingCharacters(in: .whitespacesAndNewlines) == clean else {
                return
            }
            await stop()
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
