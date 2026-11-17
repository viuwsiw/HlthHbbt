import SwiftUI
import UIKit

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}

enum ThemeMode: String, CaseIterable, Codable {
    case SYSTEM, LIGHT, DARK, BLACK
    var title: String {
        switch self {
        case .SYSTEM: return "Авто"
        case .LIGHT: return "Светлая"
        case .DARK: return "Тёмная"
        case .BLACK: return "Чёрная"
        }
    }
}

struct AccentOption {
    let title: String
    let light: (UInt32, UInt32)
    let dark: (UInt32, UInt32)
}

let accentOptions: [AccentOption] = [
    AccentOption(title: "Мята", light: (0x17B077, 0x14A3B8), dark: (0x2FD394, 0x2CC3D6)),
    AccentOption(title: "Океан", light: (0x2563EB, 0x06B6D4), dark: (0x60A5FA, 0x22D3EE)),
    AccentOption(title: "Лаванда", light: (0x7C3AED, 0xDB2777), dark: (0xA78BFA, 0xF472B6)),
    AccentOption(title: "Закат", light: (0xEA580C, 0xE11D48), dark: (0xFB923C, 0xFB7185)),
    AccentOption(title: "Лайм", light: (0x65A30D, 0x0D9488), dark: (0xA3E635, 0x2DD4BF)),
    AccentOption(title: "AMOLED", light: (0x000000, 0x262626), dark: (0xFFFFFF, 0xBDBDBD)),
    AccentOption(title: "Графит", light: (0x1F1F1F, 0x6B6B6B), dark: (0xE0E0E0, 0x8A8A8A)),
]

let habitColors: [Color] = ([0x22C55E, 0x06B6D4, 0xF59E0B, 0xEF4444, 0x8B5CF6, 0xEC4899, 0x3B82F6, 0x84CC16] as [UInt32]).map { Color(hex: $0) }

func habitColor(_ i: Int) -> Color { habitColors[((i % habitColors.count) + habitColors.count) % habitColors.count] }

struct Palette {
    var isDark: Bool
    var isBlack: Bool
    var accent: Color
    var accent2: Color
    var base: Color
    var blobs: [Color]
    var text: Color
    var text2: Color
    var onAccent: Color
    var danger = Color(hex: 0xE5484D)
    var water = Color(hex: 0x3AA8FF)
    var mono: Bool

    static func make(dark: Bool, black: Bool, accentIndex: Int) -> Palette {
        let opt = accentOptions.indices.contains(accentIndex) ? accentOptions[accentIndex] : accentOptions[0]
        let isDark = dark || black
        let pair = isDark ? opt.dark : opt.light
        let mono = accentIndex >= 5
        let a1 = Color(hex: pair.0), a2 = Color(hex: pair.1)
        let base: Color = black ? .black : (isDark ? Color(hex: 0x080A0C) : Color(hex: 0xF1F3F5))
        let blobOpacity: Double = black ? 0.28 : (isDark ? (mono ? 0.18 : 0.45) : (mono ? 0.18 : 0.5))
        let blobs = [a1, a2, a1.mix(a2), a1.mix(.white, 0.3), a2.mix(a1, 0.25)].map { $0.opacity(blobOpacity) }
        return Palette(
            isDark: isDark, isBlack: black, accent: a1, accent2: a2, base: base, blobs: blobs,
            text: black ? Color(hex: 0xF2F2F2) : (isDark ? Color(hex: 0xEAFBF3) : Color(hex: 0x0D2A21)),
            text2: black ? Color(hex: 0x9A9A9A) : (isDark ? Color(hex: 0xA6C4BA) : Color(hex: 0x3F6157)),
            onAccent: isDark ? (mono ? .black : Color(hex: 0x032016)) : .white,
            mono: mono
        )
    }

    var gradient: LinearGradient {
        LinearGradient(colors: [accent, accent2], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

extension Color {
    /// Смешение двух цветов (приблизительно, через компоненты UIColor).
    func mix(_ other: Color, _ t: Double = 0.5) -> Color {
        let a = UIColor(self), b = UIColor(other)
        var r1: CGFloat = 0, g1: CGFloat = 0, b1: CGFloat = 0, a1: CGFloat = 0
        var r2: CGFloat = 0, g2: CGFloat = 0, b2: CGFloat = 0, a2: CGFloat = 0
        a.getRed(&r1, green: &g1, blue: &b1, alpha: &a1)
        b.getRed(&r2, green: &g2, blue: &b2, alpha: &a2)
        let k = CGFloat(t)
        return Color(.sRGB, red: Double(r1 + (r2 - r1) * k), green: Double(g1 + (g2 - g1) * k),
                     blue: Double(b1 + (b2 - b1) * k), opacity: Double(a1 + (a2 - a1) * k))
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.make(dark: false, black: false, accentIndex: 0)
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

// MARK: - Liquid Glass

/// Стеклянная подложка: на iOS 26 — системное Liquid Glass, раньше — материал с бликом.
struct GlassBackground: ViewModifier {
    var cornerRadius: CGFloat = 24
    var tint: Color? = nil
    var interactive = false
    @Environment(\.palette) private var p

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content.glassEffect(glass, in: shape)
        } else {
            fallback(content, shape)
        }
        #else
        fallback(content, shape)
        #endif
    }

    #if compiler(>=6.2)
    @available(iOS 26.0, *)
    private var glass: Glass {
        var g = Glass.regular
        if let tint { g = g.tint(tint) }
        if interactive { g = g.interactive() }
        return g
    }
    #endif

    private func fallback(_ content: Content, _ shape: RoundedRectangle) -> some View {
        content
            .background(.ultraThinMaterial, in: shape)
            .background((tint ?? .clear).opacity(0.35), in: shape)
            .overlay(
                shape.strokeBorder(
                    LinearGradient(colors: [.white.opacity(p.isDark ? 0.35 : 0.9), .white.opacity(0.05)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    lineWidth: 1)
            )
            .shadow(color: .black.opacity(p.isDark ? 0.3 : 0.08), radius: 16, y: 8)
    }
}

extension View {
    func glass(_ radius: CGFloat = 24, tint: Color? = nil, interactive: Bool = false) -> some View {
        modifier(GlassBackground(cornerRadius: radius, tint: tint, interactive: interactive))
    }

    func glassCard(_ padding: CGFloat = 16) -> some View {
        self.padding(padding).frame(maxWidth: .infinity, alignment: .leading).glass()
    }
}

/// Контейнер, объединяющий соседние стеклянные элементы (на iOS 26 — GlassEffectContainer).
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder var content: () -> Content
    var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content() }
        } else {
            content()
        }
        #else
        content()
        #endif
    }
}

/// Кнопка-капсула: на iOS 26 — системные стили .glass / .glassProminent, раньше — своё стекло.
struct GlassButton: View {
    let title: String
    var systemImage: String? = nil
    var prominent = false
    var role: ButtonRole? = nil
    let action: () -> Void
    @Environment(\.palette) private var p

    private var label: some View {
        HStack(spacing: 8) {
            if let systemImage { Image(systemName: systemImage) }
            Text(title).fontWeight(.semibold)
        }
        .frame(maxWidth: .infinity)
    }

    private var fg: Color { prominent ? p.onAccent : (role == .destructive ? p.danger : p.text) }

    var body: some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            if prominent {
                Button(role: role, action: action) { label.padding(.vertical, 6).foregroundStyle(fg) }
                    .buttonStyle(.glassProminent).tint(p.accent).controlSize(.large)
            } else {
                Button(role: role, action: action) { label.padding(.vertical, 6).foregroundStyle(fg) }
                    .buttonStyle(.glass).controlSize(.large)
            }
        } else {
            legacy
        }
        #else
        legacy
        #endif
    }

    private var legacy: some View {
        Button(role: role, action: action) {
            label
                .padding(.vertical, 14)
                .foregroundStyle(fg)
                .background { if prominent { Capsule().fill(p.gradient) } }
                .glass(28, tint: prominent ? p.accent.opacity(0.6) : nil, interactive: true)
        }
        .buttonStyle(.plain)
    }
}

/// Фон-аврора из размытых пятен акцентного цвета.
struct LiquidBackground: View {
    @Environment(\.palette) private var p
    var animated = false
    @State private var phase = false

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            ZStack {
                p.base
                ForEach(0..<p.blobs.count, id: \.self) { i in
                    let pos = Self.positions[i]
                    Circle()
                        .fill(p.blobs[i])
                        .frame(width: w * 0.9, height: w * 0.9)
                        .position(x: w * (phase ? pos.2 : pos.0), y: h * (phase ? pos.3 : pos.1))
                        .blur(radius: 70)
                }
            }
            .onAppear {
                guard animated else { return }
                withAnimation(.easeInOut(duration: 14).repeatForever(autoreverses: true)) { phase.toggle() }
            }
        }
        .ignoresSafeArea()
    }

    private static let positions: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
        (0.1, 0.05, 0.25, 0.15), (0.95, 0.2, 0.75, 0.3), (0.2, 0.6, 0.4, 0.5),
        (0.9, 0.85, 0.7, 0.75), (0.5, 1.0, 0.3, 0.9),
    ]
}

struct SectionTitle: View {
    let text: String
    @Environment(\.palette) private var p
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(.footnote.weight(.semibold)).textCase(.uppercase)
            .foregroundStyle(p.text2).padding(.horizontal, 6).padding(.top, 6)
    }
}

/// Стеклянное поле ввода.
struct GlassField: View {
    let title: String
    @Binding var text: String
    var secure = false
    var keyboard: UIKeyboardType = .default
    @Environment(\.palette) private var p

    var body: some View {
        Group {
            if secure {
                SecureField(title, text: $text)
            } else {
                TextField(title, text: $text).keyboardType(keyboard)
            }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .padding(.horizontal, 18).padding(.vertical, 14)
        .foregroundStyle(p.text)
        .glass(20)
    }
}

/// Аватар: встроенный (preset:N), фото (data) или первая буква имени.
struct AvatarView: View {
    let avatar: String?
    var photo: Data? = nil
    let name: String
    var size: CGFloat = 48
    @Environment(\.palette) private var p

    var body: some View {
        ZStack {
            if let photo, let img = UIImage(data: photo) {
                Image(uiImage: img).resizable().scaledToFill()
            } else if let a = avatar, a.hasPrefix("preset:"), let i = Int(a.dropFirst(7)) {
                Image("avatar_\(max(1, min(16, i + 1)))").resizable().scaledToFill()
            } else {
                p.gradient
                Text(String(name.trimmingCharacters(in: .whitespaces).first ?? "?").uppercased())
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundStyle(p.onAccent)
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().strokeBorder(LinearGradient(colors: [.white.opacity(0.9), p.accent.opacity(0.6), .white.opacity(0.1)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: max(1.5, size / 32)))
    }
}
