import SwiftUI
import FamilyControls
import UIKit

@main
struct TakeABreakApp: App {
    var body: some Scene { WindowGroup { HomeView() } }
}

private enum Page: String, CaseIterable {
    case timer = "计时"
    case settings = "设置"
    case ready = "准备"
}

private enum Theme {
    static let paper = Color(red: 0.98, green: 0.97, blue: 0.94)
    static let ink = Color(red: 0.10, green: 0.22, blue: 0.19)
    static let green = Color(red: 0.12, green: 0.39, blue: 0.32)
    static let mint = Color(red: 0.86, green: 0.93, blue: 0.88)
    static let gold = Color(red: 0.78, green: 0.56, blue: 0.25)
    static let muted = Color(red: 0.41, green: 0.50, blue: 0.46)
}

struct HomeView: View {
    @State private var page: Page = .timer
    @State private var selection = BreakState.selection
    @State private var showingPicker = false
    @State private var workMinutes = BreakState.workMinutes
    @State private var restMinutes = BreakState.restMinutes
    @State private var requiredCharacters = BreakState.requiredCharacters
    @State private var reflection = ""
    @State private var message = ""
    @State private var isEnabled = BreakState.enabled
    @State private var unlockAt = BreakState.unlockAt
    @State private var authorizationStatus = AuthorizationCenter.shared.authorizationStatus
    @State private var holdProgress: CGFloat = 0
    @State private var holdStart: Date?
    @State private var holdCanceled = false
    @State private var holdCompleted = false
    @State private var holdWorkItems: [DispatchWorkItem] = []

    private var selectionCount: Int {
        selection.applicationTokens.count + selection.categoryTokens.count + selection.webDomainTokens.count
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
                header
                tabs
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        switch page {
                        case .timer: timerPage
                        case .settings: settingsPage
                        case .ready: readyPage
                        }
                        if !message.isEmpty {
                            Text(message)
                                .font(.footnote)
                                .foregroundStyle(Theme.green)
                                .padding(.horizontal, 8)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, 24)
                    .padding(.bottom, 36)
                }
            }
            .padding(.horizontal, 24)
            .background(Theme.paper.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
        .familyActivityPicker(isPresented: $showingPicker, selection: $selection)
        .onChange(of: showingPicker) { visible in
            if !visible { persistSelection() }
        }
        .onChange(of: workMinutes) { _ in saveSettings() }
        .onChange(of: restMinutes) { _ in saveSettings() }
        .onChange(of: requiredCharacters) { _ in saveSettings() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            refreshState()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in
            cancelHold()
        }
        .onAppear { refreshState() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("TAKE A BREATH")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .tracking(2)
                .foregroundStyle(Theme.green)
            Text("歇一会儿")
                .font(.system(size: 33, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
        }
        .padding(.top, 18)
        .padding(.bottom, 22)
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            ForEach(Page.allCases, id: \.self) { item in
                Button {
                    cancelHold()
                    page = item
                    message = ""
                } label: {
                    Text(item.rawValue)
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                        .foregroundStyle(Theme.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(item == page ? Color.white : Color.clear,
                                    in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Theme.mint, in: RoundedRectangle(cornerRadius: 18))
    }

    private var timerPage: some View {
        VStack(alignment: .leading, spacing: 24) {
            GeometryReader { geometry in
                let diameter = min(geometry.size.width, 340)
                ZStack {
                    Circle()
                        .stroke(Theme.mint, lineWidth: 6)
                    Circle()
                        .trim(from: 0, to: holdProgress)
                        .stroke(Theme.gold, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Circle()
                        .fill(unlockAt != nil ? Theme.gold : isEnabled ? Theme.ink : Theme.green)
                        .padding(15)
                    VStack(spacing: 12) {
                        Text(unlockAt != nil ? "休息一下下" : isEnabled ? "正在计时" : "启动")
                            .font(.system(size: 29, weight: .bold, design: .rounded))
                            .foregroundStyle(.white)
                        Text(isEnabled
                             ? "已选 \(selectionCount) 项 · \(workMinutes) 分钟后提醒"
                             : "选择 App 后开启提醒")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.mint)
                            .multilineTextAlignment(.center)
                        Text(holdStart != nil && isEnabled ? "继续按住…" :
                             isEnabled ? "系统正在计时" : "轻点开始")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(34)
                }
                .frame(width: diameter, height: diameter)
                .contentShape(Circle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { handleDrag($0) }
                        .onEnded { _ in endTouch() }
                )
                .frame(maxWidth: .infinity)
            }
            .frame(height: 350)

            Text(isEnabled ? "长按结束并清零" : "轻点开启休息提醒")
                .font(.system(size: 14))
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity)

            if let unlockAt {
                restCard(unlockAt: unlockAt)
            } else {
                Text("这是一个用来防止你刷刷手机的软件，献给无法停下来的我们。")
                    .font(.system(size: 19))
                    .foregroundStyle(Theme.ink)
            }
            Text("iPhone 由系统统计所选 App、网站和类别的使用时间。当前无法在主控中显示精确累计秒数。")
                .font(.footnote)
                .foregroundStyle(Theme.muted)
        }
    }

    private func restCard(unlockAt: Date) -> some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("休息时间")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.green)
            Text("你现在觉得怎么样？")
                .font(.system(size: 23, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
            Text("这段时间里感觉还不错吗？来，深呼吸。这回不知道你是在娱乐，又或者在学习、工作，也不知道是否疲倦或是兴奋，但至少在这段时间里，应该属于你自己。")
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let remaining = max(0, Int(ceil(unlockAt.timeIntervalSince(context.date) / 60)))
                Text("还有 \(remaining) 分钟自动解锁")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Theme.green)
            }
            Text("如果现在要做事情的话，让我们稍微复盘一下吧？你觉得如何呢？说说你现在的想法吧。")
                .font(.system(size: 15))
                .foregroundStyle(Theme.ink)
            ZStack(alignment: .topLeading) {
                if reflection.isEmpty {
                    Text("我现在……")
                        .foregroundStyle(Theme.muted)
                        .padding(.top, 12)
                        .padding(.leading, 8)
                }
                TextEditor(text: $reflection)
                    .frame(height: 116)
                    .scrollContentBackground(.hidden)
            }
            .padding(8)
            .background(Theme.paper, in: RoundedRectangle(cornerRadius: 14))
            Text("\(reflection.trimmingCharacters(in: .whitespacesAndNewlines).count) / \(requiredCharacters) 字")
                .font(.footnote)
                .foregroundStyle(Theme.muted)
            Button("我准备好了") { unlock() }
                .buttonStyle(.borderedProminent)
                .tint(Theme.green)
                .disabled(reflection.trimmingCharacters(in: .whitespacesAndNewlines).count < requiredCharacters)
        }
        .padding(22)
        .background(.white, in: RoundedRectangle(cornerRadius: 24))
    }

    private var settingsPage: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("设定节奏")
                .font(.system(size: 23, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.ink)
            VStack(alignment: .leading, spacing: 20) {
                Stepper("多久提醒一次：\(workMinutes) 分钟", value: $workMinutes, in: 1...240)
                Text("分钟 · 所选 App、类别和网站的累计使用时间")
                    .font(.footnote).foregroundStyle(Theme.muted)
                Stepper("休息多久：\(restMinutes) 分钟", value: $restMinutes, in: 15...120)
                Text("分钟 · 到时自动解锁，iPhone 最少 15 分钟")
                    .font(.footnote).foregroundStyle(Theme.muted)
                Stepper("提前解锁写多少字：\(requiredCharacters) 字",
                        value: $requiredCharacters, in: 1...200)
                Text("字 · 可使用系统输入法的语音转文字")
                    .font(.footnote).foregroundStyle(Theme.muted)
            }
            .disabled(isEnabled)
            .padding(20)
            .background(.white, in: RoundedRectangle(cornerRadius: 22))

            Button("选择要提醒的 App / 类别 / 网站") { showingPicker = true }
                .buttonStyle(.borderedProminent)
                .tint(Theme.green)
                .disabled(isEnabled)
            Text("已选 \(selectionCount) 项。运行中会锁定时长与选择，长按计时页主控结束后可修改。")
                .font(.footnote)
                .foregroundStyle(Theme.muted)
            Text("iPhone 到点后由屏幕使用时间拦截所选内容；系统不会替本应用暂停其他 App 的视频播放。")
                .font(.footnote)
                .foregroundStyle(Theme.muted)
        }
    }

    private var readyPage: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 12) {
                Text("运行准备")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.mint)
                Text(authorizationStatus == .approved && selectionCount > 0
                     ? "已就绪，可以开始" : "还需完成准备")
                    .font(.system(size: 25, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                Text("授权屏幕使用时间，并选择需要提醒的 App、类别或网站。")
                    .foregroundStyle(Theme.mint)
            }
            .padding(22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.ink, in: RoundedRectangle(cornerRadius: 24))

            readyRow("屏幕使用时间", detail: "允许系统统计所选内容并显示拦截页",
                     done: authorizationStatus == .approved)
            readyRow("提醒范围", detail: "已选 \(selectionCount) 项", done: selectionCount > 0)
            Button("逐项配置运行准备") { Task { await prepare() } }
                .buttonStyle(.borderedProminent)
                .tint(Theme.green)
                .frame(maxWidth: .infinity)
            Text("iPhone 的授权由系统页面完成。提醒只会覆盖你选择的内容。")
                .font(.footnote)
                .foregroundStyle(Theme.muted)
        }
    }

    private func readyRow(_ title: String, detail: String, done: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: done ? "checkmark.circle.fill" : "circle.dashed")
                .foregroundStyle(done ? Theme.green : Theme.gold)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: 17, weight: .semibold))
                Text(detail).font(.footnote).foregroundStyle(Theme.muted)
            }
            Spacer()
            Text(done ? "已完成" : "待完成")
                .font(.footnote)
                .foregroundStyle(done ? Theme.green : Theme.gold)
        }
        .padding(18)
        .background(.white, in: RoundedRectangle(cornerRadius: 18))
    }

    private func handleDrag(_ value: DragGesture.Value) {
        guard !holdCanceled && !holdCompleted else { return }
        if holdStart == nil { beginHold() }
        if abs(value.translation.width) > 40 || abs(value.translation.height) > 40 {
            cancelHold()
            holdCanceled = true
        }
    }

    private func beginHold() {
        let began = Date()
        holdStart = began
        guard isEnabled else { return }
        withAnimation(.linear(duration: 1.6)) { holdProgress = 1 }
        let moments: [Double] = [0.30, 0.50, 0.68, 0.84, 0.98, 1.10, 1.20, 1.28]
        for (index, moment) in moments.enumerated() {
            let item = DispatchWorkItem {
                guard self.holdStart == began && !self.holdCanceled else { return }
                UIImpactFeedbackGenerator(style: .light)
                    .impactOccurred(intensity: 0.25 + CGFloat(index) * 0.08)
            }
            holdWorkItems.append(item)
            DispatchQueue.main.asyncAfter(deadline: .now() + moment, execute: item)
        }
        let completion = DispatchWorkItem {
            guard self.holdStart == began && !self.holdCanceled else { return }
            self.holdCompleted = true
            self.holdStart = nil
            self.holdWorkItems = []
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            BreakState.stop()
            self.isEnabled = false
            self.unlockAt = nil
            self.message = "已结束并清零"
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                withAnimation(.easeOut(duration: 0.2)) { self.holdProgress = 0 }
            }
        }
        holdWorkItems.append(completion)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6, execute: completion)
    }

    private func endTouch() {
        if holdCompleted {
            holdCompleted = false
            return
        }
        let canceled = holdCanceled
        cancelHold()
        holdCanceled = false
        if !canceled { tappedOrb() }
    }

    private func cancelHold() {
        holdWorkItems.forEach { $0.cancel() }
        holdWorkItems = []
        holdStart = nil
        withAnimation(.easeOut(duration: 0.15)) { holdProgress = 0 }
    }

    private func tappedOrb() {
        UISelectionFeedbackGenerator().selectionChanged()
        if isEnabled {
            message = "系统正在累计所选内容的使用时间；长按主控可结束。"
        } else {
            Task { await start() }
        }
    }

    private func saveSettings() {
        guard !isEnabled else { return }
        BreakState.defaults.set(workMinutes, forKey: "workMinutes")
        BreakState.defaults.set(restMinutes, forKey: "restMinutes")
        BreakState.defaults.set(requiredCharacters, forKey: "requiredCharacters")
    }

    private func persistSelection() {
        guard !isEnabled else { return }
        do { try BreakState.saveSelection(selection) }
        catch { message = "保存选择失败：\(error.localizedDescription)" }
    }

    private func prepare() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            authorizationStatus = AuthorizationCenter.shared.authorizationStatus
            if selectionCount == 0 {
                showingPicker = true
                message = "授权已完成，请选择要提醒的内容。"
            } else {
                message = "运行准备已完成，可以返回计时页开启提醒。"
            }
        } catch {
            authorizationStatus = AuthorizationCenter.shared.authorizationStatus
            message = "授权未完成：\(error.localizedDescription)"
        }
    }

    private func start() async {
        guard selectionCount > 0 else {
            page = .ready
            message = "请先选择至少一个 App、类别或网站。"
            return
        }
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            try BreakState.saveSelection(selection)
            saveSettings()
            BreakState.defaults.set(true, forKey: "enabled")
            try BreakState.restartUsage()
            authorizationStatus = AuthorizationCenter.shared.authorizationStatus
            isEnabled = true
            unlockAt = nil
            message = "已开始由系统统计所选内容的使用时间。"
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
            unlockAt = nil
            message = "已解锁，重新开始计时。"
        } catch {
            BreakState.stop()
            isEnabled = false
            unlockAt = nil
            message = "解锁失败，提醒已停止：\(error.localizedDescription)"
        }
    }

    private func refreshState() {
        if let end = BreakState.unlockAt, end <= Date() {
            do { try BreakState.restartUsage() }
            catch {
                BreakState.stop()
                message = "自动解锁后未能重新开始计时：\(error.localizedDescription)"
            }
        }
        isEnabled = BreakState.enabled
        unlockAt = BreakState.unlockAt
        authorizationStatus = AuthorizationCenter.shared.authorizationStatus
    }
}
