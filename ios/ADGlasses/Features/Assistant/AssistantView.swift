import Foundation
import SwiftUI
import UIKit

struct AssistantView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var glasses: GlassesManager
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let openSettings: () -> Void

    @State private var showsHistory = false
    @State private var confirmsNewConversation = false
    @FocusState private var composerFocused: Bool

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        if app.conversation.isEmpty {
                            AssistantWelcome(
                                isListening: app.isTranscribing ||
                                    glasses.assistantInputState == .listening
                            )
                        } else {
                            conversationHeader

                            ForEach(app.conversation) { message in
                                ConversationBubble(message: message)
                                    .id(message.id)
                            }

                            if app.isGenerating {
                                HStack(spacing: 9) {
                                    AssistantAvatar(size: 28)
                                    ProgressView()
                                        .controlSize(.small)
                                    Text("Thinking")
                                        .font(.footnote.weight(.medium))
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                }
                                .accessibilityElement(children: .combine)
                                .accessibilityLabel("AD is preparing a response")
                            }

                            if let notice = app.conversationNotice {
                                ConversationNotice(text: notice) {
                                    app.conversationNotice = nil
                                }
                            }
                        }
                    }
                    .frame(maxWidth: 720)
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, 112)
                    .frame(maxWidth: .infinity)
                }
                .scrollDismissesKeyboard(.interactively)
                .background(Color(uiColor: .systemGroupedBackground))
                .onChange(of: app.conversation.count) { _, _ in
                    guard let lastID = app.conversation.last?.id else { return }
                    if reduceMotion {
                        proxy.scrollTo(lastID, anchor: .bottom)
                    } else {
                        withAnimation(.snappy) {
                            proxy.scrollTo(lastID, anchor: .bottom)
                        }
                    }
                }
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showsHistory = true
                    } label: {
                        Image(systemName: "clock.arrow.circlepath")
                    }
                    .accessibilityLabel("Conversation history")
                    .disabled(app.isTranscribing || app.isStoppingTranscription)
                }

                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button {
                        if app.conversation.isEmpty {
                            app.startNewConversation()
                            composerFocused = true
                        } else {
                            confirmsNewConversation = true
                        }
                    } label: {
                        Image(systemName: "square.and.pencil")
                    }
                    .accessibilityLabel("New conversation")
                    .disabled(app.isTranscribing || app.isStoppingTranscription)

                    Button(action: openSettings) {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                AssistantComposer(focused: $composerFocused)
            }
            .sheet(isPresented: $showsHistory) {
                ConversationHistorySheet()
                    .environmentObject(app)
            }
            .confirmationDialog(
                "Start a new conversation?",
                isPresented: $confirmsNewConversation,
                titleVisibility: .visible
            ) {
                Button("New conversation") {
                    app.startNewConversation()
                    composerFocused = true
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("The current conversation is saved locally and will remain in History.")
            }
        }
    }

    private var conversationHeader: some View {
        HStack(spacing: 10) {
            AssistantAvatar(size: 34)
            VStack(alignment: .leading, spacing: 1) {
                Text("AD")
                    .font(.subheadline.weight(.semibold))
                Text("Current conversation")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .accessibilityElement(children: .combine)
    }
}

private struct AssistantWelcome: View {
    let isListening: Bool

    var body: some View {
        VStack(spacing: 10) {
            hero
            AssistantSignalVisual(isListening: isListening)
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack(alignment: .top) {
                AssistantAvatar(size: 54)
                Spacer()
            }

            VStack(alignment: .leading, spacing: 7) {
                Text("Ask")
                    .font(.largeTitle.bold())
                    .foregroundStyle(.white)
                Text("Talk it through, ask a question, or pick up where your glasses left off.")
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.82))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, minHeight: 220, alignment: .leading)
        .background {
            ZStack(alignment: .topTrailing) {
                LinearGradient(
                    colors: [.indigo, .blue],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                Circle()
                    .fill(.white.opacity(0.11))
                    .frame(width: 190, height: 190)
                    .offset(x: 58, y: -72)
                Circle()
                    .fill(.cyan.opacity(0.18))
                    .frame(width: 130, height: 130)
                    .offset(x: 34, y: 130)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

private struct AssistantSignalVisual: View {
    let isListening: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reduceMotion)) { timeline in
            let time = timeline.date.timeIntervalSinceReferenceDate
            let pulse = reduceMotion ? 0.5 : normalizedWave(time: time, speed: isListening ? 1.9 : 0.72)

            ZStack {
                ForEach(0..<3, id: \.self) { ring in
                    Circle()
                        .stroke(
                            LinearGradient(
                                colors: [
                                    Color.indigo.opacity(ringOpacity(ring: ring, pulse: pulse)),
                                    Color.cyan.opacity(ringOpacity(ring: ring, pulse: pulse) * 0.72)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1
                        )
                        .frame(width: ringSize(ring), height: ringSize(ring))
                        .scaleEffect(
                            CGFloat(
                                reduceMotion
                                    ? 1
                                    : 0.97 + (pulse * 0.045) + (Double(ring) * 0.008)
                            )
                        )
                }

                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color.cyan.opacity(isListening ? 0.22 : 0.12),
                                Color.indigo.opacity(isListening ? 0.20 : 0.10),
                                Color.indigo.opacity(0.025)
                            ],
                            center: .topLeading,
                            startRadius: 0,
                            endRadius: 88
                        )
                    )
                    .frame(width: 118, height: 118)
                    .scaleEffect(CGFloat(reduceMotion ? 1 : 0.97 + (pulse * 0.055)))

                HStack(alignment: .center, spacing: 5) {
                    ForEach(0..<7, id: \.self) { index in
                        Capsule(style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [.indigo, .cyan],
                                    startPoint: .bottom,
                                    endPoint: .top
                                )
                            )
                            .frame(width: 5, height: barHeight(index: index, time: time))
                    }
                }
                .frame(height: 52)
            }
            .frame(maxWidth: .infinity, minHeight: 238)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(isListening ? "Listening" : "AD ready")
    }

    private func normalizedWave(time: TimeInterval, speed: Double) -> Double {
        (sin(time * speed) + 1) / 2
    }

    private func ringSize(_ ring: Int) -> CGFloat {
        let sizes: [CGFloat] = [118, 164, 210]
        return sizes[ring]
    }

    private func ringOpacity(ring: Int, pulse: Double) -> Double {
        let base = isListening ? 0.18 : 0.09
        let falloff = Double(ring) * 0.025
        return max(0.04, base + (pulse * 0.08) - falloff)
    }

    private func barHeight(index: Int, time: TimeInterval) -> CGFloat {
        let resting: [CGFloat] = [14, 22, 31, 40, 31, 22, 14]
        guard !reduceMotion else { return resting[index] }

        let speed = isListening ? 5.4 : 1.55
        let amplitude: CGFloat = isListening ? 34 : 9
        let baseline: CGFloat = isListening ? 9 : resting[index] - 4
        let wave = (sin((time * speed) + (Double(index) * 0.76)) + 1) / 2
        return baseline + (CGFloat(wave) * amplitude)
    }
}

private struct AssistantAvatar: View {
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(.white.opacity(0.17))
            Image(systemName: "sparkles")
                .font(.system(size: size * 0.38, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
        .background(
            LinearGradient(colors: [.indigo, .blue], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: Circle()
        )
        .overlay {
            Circle().stroke(.white.opacity(0.22), lineWidth: 0.75)
        }
        .accessibilityHidden(true)
    }
}

private struct ConversationBubble: View {
    let message: ConversationMessage

    var body: some View {
        HStack(alignment: .bottom, spacing: 9) {
            if message.role == .user {
                Spacer(minLength: 42)
            } else {
                AssistantAvatar(size: 28)
            }

            VStack(alignment: message.role == .user ? .trailing : .leading, spacing: 8) {
                if let attachment = message.imageAttachment {
                    ConversationImagePreview(attachment: attachment)
                }

                if !message.text.isEmpty {
                    Text(message.text)
                        .font(.body)
                        .foregroundStyle(message.role == .user ? .white : .primary)
                        .textSelection(.enabled)
                }
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 11)
            .background(bubbleBackground)
            .clipShape(
                UnevenRoundedRectangle(
                    topLeadingRadius: 18,
                    bottomLeadingRadius: message.role == .assistant ? 5 : 18,
                    bottomTrailingRadius: message.role == .user ? 5 : 18,
                    topTrailingRadius: 18
                )
            )

            if message.role == .assistant {
                Spacer(minLength: 42)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message.role == .user ? "You" : "AD")
        .accessibilityValue(message.text)
    }

    @ViewBuilder
    private var bubbleBackground: some View {
        if message.role == .user {
            LinearGradient(colors: [.blue, .indigo], startPoint: .topLeading, endPoint: .bottomTrailing)
        } else {
            Color(uiColor: .secondarySystemGroupedBackground)
        }
    }
}

private struct ConversationImagePreview: View {
    @EnvironmentObject private var app: AppModel

    let attachment: ConversationImageAttachment

    @State private var image: UIImage?
    @State private var didFinishLoading = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: 240, maxHeight: 240)
            } else if didFinishLoading {
                Image(systemName: "photo")
                    .font(.title2)
                    .foregroundStyle(.secondary)
                    .frame(width: 180, height: 120)
                    .background(Color(uiColor: .tertiarySystemFill))
            } else {
                ProgressView()
                    .frame(width: 180, height: 120)
                    .background(Color(uiColor: .tertiarySystemFill))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityLabel("Captured view")
        .task(id: attachment.id) {
            image = nil
            didFinishLoading = false
            defer { didFinishLoading = true }
            guard let data = await app.conversationImageData(for: attachment) else { return }
            image = UIImage(data: data)
        }
    }
}

private struct ConversationNotice: View {
    let text: String
    let dismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 11) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(.orange)
                .padding(.top, 1)
            Text(text)
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer(minLength: 4)
            Button(action: dismiss) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Dismiss")
        }
        .padding(13)
        .background(.orange.opacity(0.10), in: RoundedRectangle(cornerRadius: 15))
    }
}

private struct AssistantComposer: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let focused: FocusState<Bool>.Binding

    var body: some View {
        VStack(spacing: 8) {
            if let speechError = app.speechError {
                ConversationNotice(text: speechError) {
                    app.speechError = nil
                }
            }

            if app.isManualTranscription {
                HStack(spacing: 8) {
                    if app.isStoppingTranscription {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "waveform")
                            .foregroundStyle(.red)
                            .symbolEffect(.variableColor.iterative, isActive: !reduceMotion)
                    }
                    Text(app.isStoppingTranscription ? "Finishing transcription…" : "Listening · sends automatically")
                        .font(.caption.weight(.semibold))
                    Spacer()
                    Button(app.isStoppingTranscription ? "Stopping…" : "Cancel") {
                        Task { await app.cancelVoiceQuestion() }
                    }
                    .font(.caption.weight(.semibold))
                    .disabled(app.isStoppingTranscription)
                }
                .padding(.horizontal, 4)
            } else if app.isTranscribing || app.isStoppingTranscription {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                    Text(app.isGlassesAssistantAudioActive ? "Listening from glasses" : "Voice turn in progress")
                        .font(.caption.weight(.semibold))
                    Spacer()
                }
                .padding(.horizontal, 4)
            }

            HStack(alignment: .bottom, spacing: 8) {
                Menu {
                    Button("Photo Library", systemImage: "photo", action: {})
                        .disabled(true)
                    Button("Camera", systemImage: "camera", action: {})
                        .disabled(true)
                    Button("File", systemImage: "doc", action: {})
                        .disabled(true)
                } label: {
                    Image(systemName: "plus")
                        .font(.body.weight(.semibold))
                        .frame(width: 38, height: 38)
                }
                .accessibilityLabel("Add attachment")

                TextField("Message AD", text: $app.chatDraft, axis: .vertical)
                    .focused(focused)
                    .lineLimit(1...5)
                    .submitLabel(.send)
                    .onSubmit { app.sendChatMessage() }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(
                        Color(uiColor: .tertiarySystemFill),
                        in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                    )
                    .disabled(app.isTranscribing || app.isStoppingTranscription)

                if app.isGenerating {
                    Button(action: app.cancelResponse) {
                        Image(systemName: "stop.fill")
                            .font(.body.weight(.semibold))
                            .frame(width: 38, height: 38)
                            .foregroundStyle(.red)
                    }
                    .accessibilityLabel("Stop response")
                } else if app.isManualTranscription {
                    Button {
                        Task { await app.cancelVoiceQuestion() }
                    } label: {
                        Image(systemName: app.isStoppingTranscription ? "hourglass" : "xmark")
                            .font(.body.weight(.semibold))
                            .frame(width: 38, height: 38)
                            .foregroundStyle(.red)
                    }
                    .disabled(app.isStoppingTranscription)
                    .accessibilityLabel(app.isStoppingTranscription ? "Stopping voice input" : "Cancel voice question")
                } else if app.isTranscribing || app.isStoppingTranscription {
                    Image(systemName: app.isStoppingTranscription ? "hourglass" : "waveform")
                        .font(.body.weight(.semibold))
                        .frame(width: 38, height: 38)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("Voice turn in progress")
                } else if app.chatDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Button {
                        Task {
                            app.clearTranscript()
                            await app.startVoiceQuestion()
                        }
                    } label: {
                        Image(systemName: "mic.fill")
                            .font(.body.weight(.semibold))
                            .frame(width: 38, height: 38)
                            .foregroundStyle(.primary)
                    }
                    .accessibilityLabel("Start voice input")
                } else {
                    Button {
                        app.sendChatMessage()
                        focused.wrappedValue = false
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.body.weight(.bold))
                            .frame(width: 38, height: 38)
                            .foregroundStyle(.white)
                            .background(
                                LinearGradient(
                                    colors: [.blue, .indigo],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                in: Circle()
                            )
                    }
                    .accessibilityLabel("Send message")
                }
            }
        }
        .padding(10)
        .frame(maxWidth: 720)
        .frame(maxWidth: .infinity)
        .modifier(AssistantFloatingSurface())
        .padding(.horizontal, 10)
        .padding(.bottom, 7)
    }
}

private struct ConversationHistorySheet: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if app.conversations.isEmpty {
                    ContentUnavailableView(
                        "No conversations yet",
                        systemImage: "bubble.left.and.bubble.right",
                        description: Text("Conversations are stored locally after you send your first message.")
                    )
                } else {
                    List {
                        ForEach(app.conversations) { thread in
                            Button {
                                app.openConversation(thread.id)
                                dismiss()
                            } label: {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(thread.title)
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)
                                        Text(thread.preview)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    if thread.id == app.currentConversationID {
                                        Image(systemName: "checkmark")
                                            .font(.caption.bold())
                                            .foregroundStyle(.indigo)
                                    }
                                }
                            }
                        }
                        .onDelete { offsets in
                            let ids = offsets.map { app.conversations[$0].id }
                            Task {
                                for id in ids {
                                    await app.deleteConversation(id)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Conversations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct AssistantFloatingSurface: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @ViewBuilder
    func body(content: Content) -> some View {
        if reduceTransparency {
            content
                .background(
                    Color(uiColor: .secondarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 24, style: .continuous)
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .stroke(Color(uiColor: .separator).opacity(0.45), lineWidth: 0.5)
                }
        } else if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: .rect(cornerRadius: 24))
        } else {
            content.background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }
}
