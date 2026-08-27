import AVFoundation
import Foundation
import Speech

#if compiler(>=6.2)
@available(iOS 26.0, *)
@MainActor
final class SpeechAnalyzerTranscriber: SpeechTranscribing {
    let engineName = "Apple SpeechAnalyzer (on-device)"
    var onUpdate: ((SpeechTranscriptionSnapshot) -> Void)?
    var onError: ((Error) -> Void)?

    private(set) var snapshot: SpeechTranscriptionSnapshot {
        didSet { onUpdate?(snapshot) }
    }

    private let requestedLocale: Locale
    private let audioEngine = AVAudioEngine()
    private var analyzer: SpeechAnalyzer?
    private var transcriber: SpeechTranscriber?
    private var converter: AnalyzerInputConverter?
    private var analyzerInputContinuation: AsyncStream<AnalyzerInput>.Continuation?
    private var audioContinuation: AsyncStream<AVAudioPCMBuffer>.Continuation?
    private var audioTask: Task<Void, Never>?
    private var resultTask: Task<Void, Never>?
    private var finalizedTranscript = ""

    init(locale: Locale) {
        requestedLocale = locale
        snapshot = SpeechTranscriptionSnapshot(
            transcript: "",
            isRunning: false,
            engineName: engineName
        )
    }

    func start() async throws {
        if snapshot.isRunning { return }
        try await SpeechPermissions.requestAll()

        guard let locale = SpeechTranscriber.supportedLocale(equivalentTo: requestedLocale) else {
            throw SpeechTranscriptionError.localeUnsupported
        }

        let module = SpeechTranscriber(locale: locale, preset: .progressiveLiveTranscription)
        if let installationRequest = try await AssetInventory.assetInstallationRequest(supporting: [module]) {
            try await installationRequest.downloadAndInstall()
        }

        guard let analyzerFormat = await SpeechAnalyzer.bestAvailableAudioFormat(compatibleWith: [module]) else {
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        let analyzer = SpeechAnalyzer(modules: [module])
        let converter = AnalyzerInputConverter(analyzerFormat: analyzerFormat)
        let (analyzerInputs, analyzerInputContinuation) = AsyncStream.makeStream(of: AnalyzerInput.self)

        self.analyzer = analyzer
        self.transcriber = module
        self.converter = converter
        self.analyzerInputContinuation = analyzerInputContinuation

        try await analyzer.start(inputSequence: analyzerInputs)
        startResultTask(for: module)

        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playAndRecord, mode: .spokenAudio, options: [.duckOthers, .allowBluetooth])
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        guard format.sampleRate > 0 else {
            await analyzer.cancelAndFinishNow()
            throw SpeechTranscriptionError.failedToCreateAudioInput
        }

        let (audioStream, audioContinuation) = AsyncStream.makeStream(of: AVAudioPCMBuffer.self)
        self.audioContinuation = audioContinuation

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: format) { buffer, _ in
            audioContinuation.yield(AVAudioPCMBuffer(copying: buffer))
        }

        audioTask = Task { [weak self] in
            guard let self else { return }
            for await buffer in audioStream {
                do {
                    for input in try converter.convert(buffer, at: nil) {
                        analyzerInputContinuation.yield(input)
                    }
                } catch {
                    onError?(error)
                }
            }

            do {
                for input in try converter.flush() {
                    analyzerInputContinuation.yield(input)
                }
            } catch {
                onError?(error)
            }
            analyzerInputContinuation.finish()
        }

        audioEngine.prepare()
        do {
            try audioEngine.start()
            snapshot.isRunning = true
        } catch {
            inputNode.removeTap(onBus: 0)
            audioContinuation.finish()
            await analyzer.cancelAndFinishNow()
            throw error
        }
    }

    func stop() async {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }

        audioContinuation?.finish()
        await audioTask?.value
        audioTask = nil
        audioContinuation = nil

        if let analyzer {
            do {
                try await analyzer.finalizeAndFinishThroughEndOfInput()
            } catch {
                onError?(error)
            }
        }

        await resultTask?.value
        resultTask = nil
        analyzerInputContinuation = nil
        converter = nil
        transcriber = nil
        analyzer = nil
        snapshot.isRunning = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func resetTranscript() {
        finalizedTranscript = ""
        snapshot.transcript = ""
    }

    private func startResultTask(for module: SpeechTranscriber) {
        finalizedTranscript = ""
        resultTask = Task { [weak self] in
            guard let self else { return }
            var volatileTranscript = ""

            do {
                for try await result in module.results {
                    let text = String(result.text.characters)
                    if result.isFinal {
                        finalizedTranscript += text
                        volatileTranscript = ""
                    } else {
                        volatileTranscript = text
                    }
                    snapshot.transcript = finalizedTranscript + volatileTranscript
                }
            } catch is CancellationError {
                return
            } catch {
                onError?(error)
            }
        }
    }
}
#endif
