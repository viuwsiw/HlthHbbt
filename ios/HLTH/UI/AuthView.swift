import SwiftUI

struct AuthView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var register = false
    @State private var username = ""
    @State private var password = ""
    @State private var name = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Image("logo").resizable().scaledToFit().frame(width: 110, height: 110)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .shadow(color: p.accent.opacity(0.4), radius: 24, y: 10)
                    .padding(.top, 50)
                Text("HLTH HBTy").font(.largeTitle.bold()).foregroundStyle(p.text)
                Text("Полезные привычки, вода, сон и мессенджер с шифрованием")
                    .font(.subheadline).foregroundStyle(p.text2).multilineTextAlignment(.center)

                GlassGroup {
                    HStack(spacing: 0) {
                        tab("Вход", !register) { register = false }
                        tab("Регистрация", register) { register = true }
                    }
                    .padding(4)
                    .glass(26)
                }
                .padding(.top, 10)

                VStack(spacing: 12) {
                    if register { GlassField(title: "Имя", text: $name).textInputAutocapitalization(.words) }
                    GlassField(title: "Логин", text: $username)
                    GlassField(title: "Пароль", text: $password, secure: true)
                }

                if let error {
                    Text(error).font(.footnote).foregroundStyle(p.danger).multilineTextAlignment(.center)
                }

                GlassButton(title: busy ? "Подождите…" : (register ? "Создать аккаунт" : "Войти"), prominent: true) { submit() }
                    .disabled(busy || username.isEmpty || password.isEmpty)

                Text("Аккаунт общий с Android-версией: войдите тем же логином — привычки, профиль и переписка подтянутся из облачной копии.")
                    .font(.caption).foregroundStyle(p.text2).multilineTextAlignment(.center).padding(.top, 6)
            }
            .padding(24)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func tab(_ title: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        Button {
            withAnimation(.spring(duration: 0.3)) { action(); error = nil }
        } label: {
            Text(title).fontWeight(.semibold)
                .frame(maxWidth: .infinity).padding(.vertical, 10)
                .foregroundStyle(selected ? p.onAccent : p.text)
                .background { if selected { Capsule().fill(p.gradient) } }
        }
        .buttonStyle(.plain)
    }

    private func submit() {
        busy = true
        error = nil
        Task {
            do {
                if register { try await app.register(username: username, password: password, displayName: name) }
                else { try await app.login(username: username, password: password) }
                Reminders.shared.requestPermission()
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
        }
    }
}
