import UIKit

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

import SwiftUI
import Shared

struct ContentView: View {
    @State private var fromTokiPona = true
    @State private var targetLanguage = "en"
    @State private var query = ""
    @State private var result = ""
    @State private var keyboardHeight: CGFloat = 0
    @FocusState private var isEditing: Bool

    var body: some View {
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
