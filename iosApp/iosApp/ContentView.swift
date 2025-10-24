import UIKit
import SwiftUI
import Shared

class ModelDownloader: NSObject, ObservableObject, URLSessionDownloadDelegate {
    @Published var progress: Double = 0.0
    @Published var downloadedSizeGB: Double = 0.0
    @Published var totalSizeGB: Double = 0.0
    @Published var isFinished: Bool = false

    private var destinationURL: URL?
    private var tempURL: URL?
    private var session: URLSession?

    override init() {
        super.init()
        let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let modelsFolderURL = documentsURL.appendingPathComponent("models", isDirectory: true)
        let existingFileURL = modelsFolderURL.appendingPathComponent("tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf")
        if FileManager.default.fileExists(atPath: existingFileURL.path) {
            let size = fileSizeInGB(url: existingFileURL)
            self.downloadedSizeGB = size
            self.totalSizeGB = size
            self.progress = 1
            self.isFinished = true
            print("✅ Model found at launch: \(String(format: "%.2f", size)) B")
        }
    }

    func startDownload(from urlString: String, to destinationPath: String) {
        guard let url = URL(string: urlString) else { return }

        let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let modelsFolderURL = documentsURL.appendingPathComponent("models", isDirectory: true)
        if !FileManager.default.fileExists(atPath: modelsFolderURL.path) {
            try? FileManager.default.createDirectory(at: modelsFolderURL, withIntermediateDirectories: true)
        }
        let existingFileURL = modelsFolderURL.appendingPathComponent(destinationPath)
        if FileManager.default.fileExists(atPath: existingFileURL.path) {
            let sizeGB = fileSizeInGB(url: existingFileURL)
            print("✅ Model already exists at \(existingFileURL.path) (\(String(format: "%.2f", sizeGB)) GiB)")
            self.downloadedSizeGB = sizeGB
            self.totalSizeGB = sizeGB
            self.progress = 1
            self.isFinished = true
            return
        }

        let tempFolderURL = FileManager.default.temporaryDirectory.appendingPathComponent("models_download", isDirectory: true)
        // Очистка временной папки если существует
        if FileManager.default.fileExists(atPath: tempFolderURL.path) {
            try? FileManager.default.removeItem(at: tempFolderURL)
        }
        try? FileManager.default.createDirectory(at: tempFolderURL, withIntermediateDirectories: true)

        destinationURL = modelsFolderURL.appendingPathComponent(destinationPath)
        tempURL = tempFolderURL.appendingPathComponent(destinationPath)

        print("⬇️ Starting download from \(urlString)")

        let config = URLSessionConfiguration.default
        session = URLSession(configuration: config, delegate: self, delegateQueue: .main)
        let task = session!.downloadTask(with: url)
        task.resume()
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64,
                    totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        guard totalBytesExpectedToWrite > 0 else { return }
        progress = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
        let downloadedGB = Double(totalBytesWritten) / (1024.0 * 1024.0 * 1024.0)
        let totalGB = Double(totalBytesExpectedToWrite) / (1024.0 * 1024.0 * 1024.0)
        downloadedSizeGB = downloadedGB
        totalSizeGB = totalGB
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didFinishDownloadingTo location: URL) {
        guard let dest = destinationURL, let tempDest = tempURL else { return }
        print("📦 Download finished, moving to temporary location \(tempDest.path)")
        try? FileManager.default.removeItem(at: tempDest)
        do {
            try FileManager.default.moveItem(at: location, to: tempDest)
            // Move from temp folder to documents models folder
            try? FileManager.default.removeItem(at: dest)
            try FileManager.default.moveItem(at: tempDest, to: dest)
            progress = 1
            downloadedSizeGB = fileSizeInGB(url: dest)
            isFinished = true
            print("✅ Model saved successfully at \(dest.path)")
        } catch {
            print("❌ Move error: \(error)")
        }
        // Удаляем временную папку
        let tempFolderURL = FileManager.default.temporaryDirectory.appendingPathComponent("models_download", isDirectory: true)
        try? FileManager.default.removeItem(at: tempFolderURL)
    }

    private func fileSizeInGB(url: URL) -> Double {
        (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.doubleValue ?? 0 / 1_073_741_824.0
    }
}

struct SelectableTextView: UIViewRepresentable {
    let text: String

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.isEditable = false
        view.isSelectable = true
        view.isScrollEnabled = true
        view.backgroundColor = UIColor.secondarySystemGroupedBackground
        view.font = UIFont.preferredFont(forTextStyle: .body)
        view.textContainerInset = UIEdgeInsets(top: 12, left: 8, bottom: 12, right: 8)
        view.layer.cornerRadius = 12
        return view
    }

    func updateUIView(_ uiView: UITextView, context: Context) {
        uiView.text = text
    }
}

struct ContentView: View {
    @StateObject private var downloader = ModelDownloader()

    @State private var fromTokiPona = true
    @State private var targetLanguage = "en"
    @State private var query = ""
    @State private var result = ""
    @State private var keyboardHeight: CGFloat = 0
    @FocusState private var isEditing: Bool

    var body: some View {
        Group {
            if !downloader.isFinished {
                // Экран загрузки модели
                VStack {
                    Text("Downloading model")
                        .font(.headline)
                    ProgressView(value: downloader.progress)
                        .progressViewStyle(.linear)
                        .frame(width: 200)
                    Text(String(format: "%.2f / %.2f GiB", downloader.downloadedSizeGB, downloader.totalSizeGB))
                        .font(.caption)
                        .monospaced()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(uiColor: .systemBackground))
            } else {
                // Основной интерфейс переводчика
                VStack(spacing: 0) {
                    ScrollViewReader { proxy in
                        ScrollView {
                            VStack(spacing: 24) {
                                TextEditor(text: $query)
                                    .focused($isEditing)
                                    .scrollContentBackground(.hidden)
                                    .frame(minHeight: 120, maxHeight: 240)
                                    .padding(12)
                                    .background(
                                        RoundedRectangle(cornerRadius: 8)
                                            .fill(Color(uiColor: .secondarySystemBackground))
                                    )

                                Button {
                                    isEditing = false // закрываем клавиатуру и сразу синхронизируем текст
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                                        result = translate(query: query,
                                                           fromTokiPona: fromTokiPona,
                                                           target: targetLanguage)
                                    }
                                } label: {
                                    Label("Translate", systemImage: "arrow.forward.circle.fill")
                                        .font(.title3)
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.borderedProminent)

                                if !result.isEmpty {
                                    SelectableTextView(text: result)
                                        .frame(minHeight: 100)
                                        .transition(.move(edge: .top).combined(with: .opacity))
                                }
                            }
                            .padding(20)
                            .id("scrollTop")
                        }
                        .scrollDismissesKeyboard(.immediately)
                    }
                }
                .padding(.bottom, keyboardHeight)
                .overlay(
                    VStack(spacing: 0) {
                        Spacer()
                        Divider()
                        HStack(spacing: 16) {
                            if fromTokiPona {
                                Text("Toki Pona")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)

                                Button {
                                    withAnimation(.snappy) { swapDirection() }
                                } label: {
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                        .font(.title3)
                                }

                                languageChips()
                                    .frame(maxWidth: .infinity)
                            } else {
                                languageChips()
                                    .frame(maxWidth: .infinity)

                                Button {
                                    withAnimation(.snappy) { swapDirection() }
                                } label: {
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                        .font(.title3)
                                }

                                Text("Toki Pona")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .padding()
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .shadow(radius: 4)
                        .animation(.spring(duration: 0.4), value: fromTokiPona)
                    }
                    .animation(.easeOut(duration: 0.25), value: keyboardHeight)
                    .padding(.bottom, keyboardHeight == 0 ? 16 : keyboardHeight + 8)
                    , alignment: .bottom
                )
                .ignoresSafeArea(.keyboard)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(uiColor: .systemBackground))
                .onAppear {
                    NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { notification in
                        if let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
                            keyboardHeight = frame.height - 34 // учесть нижний safe area
                        }
                    }
                    NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { _ in
                        keyboardHeight = 0
                    }
                }
            }
        }
        .onAppear {
            downloader.startDownload(
                from: "https://huggingface.co/NetherQuartz/tatoeba-tok-multi-gemma-2-2b-merged-Q6_K-GGUF/resolve/main/tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf",
                to: "tatoeba-tok-multi-gemma-2-2b-merged-q6_k.gguf"
            )
        }
    }

    @ViewBuilder
    private func languageChips() -> some View {
        HStack {
            ForEach([
                ("🇺🇸", "en"),
                ("🇷🇺", "ru"),
                ("🇻🇳", "vi")
            ], id: \.1) { flag, code in
                Button {
                    targetLanguage = code
                } label: {
                    Text(flag)
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(targetLanguage == code ? Color.accentColor.opacity(0.2) : .clear)
                        )
                        .animation(.easeInOut, value: targetLanguage)
                }
            }
        }
    }

    private func swapDirection() {
        fromTokiPona.toggle()
        if !result.isEmpty {
            query = result
            result = ""
        }
    }

    private func translate(query: String, fromTokiPona: Bool, target: String) -> String {

        return "(\(fromTokiPona ? "From TP" : "To TP")) → \(target): \(query)"
    }
}

#Preview {
    ContentView()
}
