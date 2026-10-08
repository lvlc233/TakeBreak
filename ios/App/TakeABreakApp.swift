import SwiftUI
import FamilyControls

@main
struct TakeABreakApp: App {
    var body: some Scene { WindowGroup { HomeView() } }
}

struct HomeView: View {
    @State private var selection = BreakState.selection
    @State private var showingPicker = false
    @State private var workMinutes = BreakState.workMinutes
    @State private var restMinutes = BreakState.restMinutes
    @State private var requiredCharacters = BreakState.requiredCharacters
    @State private var reflection = ""
    @State private var message = ""
    @State private var isEnabled = BreakState.enabled

    private var selectionCount: Int {
        selection.applicationTokens.count + selection.categoryTokens.count + selection.webDomainTokens.count
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("放下手机，\n把时间还给自己。")
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                    Text("使用选定的 App 达到时长后，系统会显示休息提醒。")
                        .foregroundStyle(.secondary)

                    GroupBox("设置") {
                        VStack(alignment: .leading, spacing: 16) {
                            Stepper("使用 \(workMinutes) 分钟后提醒", value: $workMinutes, in: 1...240, step: 5)
                            Stepper("休息 \(restMinutes) 分钟自动解锁", value: $restMinutes, in: 15...120, step: 5)
                            Stepper("提前解锁写 \(requiredCharacters) 字", value: $requiredCharacters, in: 1...200, step: 5)
                            Button("选择要提醒的 App / 类别") { showingPicker = true }
                            Text("已选 \(selectionCount) 项")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }

                    if let unlockAt = BreakState.unlockAt, unlockAt > Date() {
                        GroupBox("正在休息") {
                            VStack(alignment: .leading, spacing: 12) {
                                TimelineView(.periodic(from: .now, by: 1)) { context in
                                    let remaining = max(0, Int(unlockAt.timeIntervalSince(context.date)))
                                    Text("约 \((remaining + 59) / 60) 分钟后自动解锁")
                                        .font(.headline)
                                }
                                Text("写下接下来真正想做的事，也可以现在解锁：")
                                TextEditor(text: $reflection)
                                    .frame(height: 110)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(.gray.opacity(0.3)))
                                Text("\(reflection.trimmingCharacters(in: .whitespacesAndNewlines).count) / \(requiredCharacters) 字")
                                    .font(.caption).foregroundStyle(.secondary)
                                Button("写完，继续使用") { unlock() }
                                    .buttonStyle(.borderedProminent)
                                    .disabled(reflection.trimmingCharacters(in: .whitespacesAndNewlines).count < requiredCharacters)
                            }
                        }
                    }

                    Button(isEnabled ? "重新开始计时" : "开启提醒") { Task { await start() } }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity)
                    if isEnabled {
                        Button("停止提醒") { BreakState.stop(); isEnabled = false; message = "已停止" }
                            .frame(maxWidth: .infinity)
                    }
                    if !message.isEmpty { Text(message).font(.footnote).foregroundStyle(.secondary) }
                    Text("iPhone 只拦截你选择的 App、网站或类别。系统拦截页不能直接输入文字；需要回到本应用写字。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                .padding(24)
            }
            .background(Color(red: 0.98, green: 0.97, blue: 0.94))
            .navigationTitle("歇一会儿")
        }
        .familyActivityPicker(isPresented: $showingPicker, selection: $selection)
        .onAppear {
            if let end = BreakState.unlockAt, end <= Date() {
                try? BreakState.restartUsage()
            }
        }
    }

    private func start() async {
        guard selectionCount > 0 else { message = "请先选择至少一个 App、网站或类别"; return }
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            try BreakState.saveSelection(selection)
            BreakState.defaults.set(workMinutes, forKey: "workMinutes")
            BreakState.defaults.set(restMinutes, forKey: "restMinutes")
            BreakState.defaults.set(requiredCharacters, forKey: "requiredCharacters")
            BreakState.defaults.set(true, forKey: "enabled")
            try BreakState.restartUsage()
            isEnabled = true
            message = "已开始计时"
        } catch {
            BreakState.stop()
            isEnabled = false
            message = "开启失败：\(error.localizedDescription)"
        }
    }

    private func unlock() {
        guard reflection.trimmingCharacters(in: .whitespacesAndNewlines).count >= requiredCharacters else { return }
        do {
            try BreakState.restartUsage()
            reflection = ""
            message = "已解锁，重新开始计时"
        } catch {
            message = "解锁失败：\(error.localizedDescription)"
        }
    }
}
