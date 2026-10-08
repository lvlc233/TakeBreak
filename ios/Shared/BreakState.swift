import Foundation
import FamilyControls
import DeviceActivity
import ManagedSettings

enum BreakState {
    static let group = "group.com.takeabreak.app"
    static let usage = DeviceActivityName("usage")
    static let cooldown = DeviceActivityName("cooldown")
    static let limit = DeviceActivityEvent.Name("limit")

    static var defaults: UserDefaults { UserDefaults(suiteName: group)! }
    static var workMinutes: Int { min(240, max(1, defaults.object(forKey: "workMinutes") as? Int ?? 30)) }
    static var restMinutes: Int { min(120, max(15, defaults.object(forKey: "restMinutes") as? Int ?? 15)) }
    static var requiredCharacters: Int { min(200, max(1, defaults.object(forKey: "requiredCharacters") as? Int ?? 20)) }
    static var enabled: Bool { defaults.bool(forKey: "enabled") }
    static var unlockAt: Date? { defaults.object(forKey: "unlockAt") as? Date }

    static var selection: FamilyActivitySelection {
        guard let data = defaults.data(forKey: "selection"),
              let result = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) else { return .init() }
        return result
    }

    static func saveSelection(_ value: FamilyActivitySelection) throws {
        defaults.set(try JSONEncoder().encode(value), forKey: "selection")
    }

    static func clearShield() {
        let store = ManagedSettingsStore()
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
        store.shield.webDomainCategories = nil
    }

    static func shieldSelected() {
        let picked = selection
        let store = ManagedSettingsStore()
        store.shield.applications = picked.applicationTokens.isEmpty ? nil : picked.applicationTokens
        store.shield.applicationCategories = picked.categoryTokens.isEmpty ? nil : .specific(picked.categoryTokens)
        store.shield.webDomains = picked.webDomainTokens.isEmpty ? nil : picked.webDomainTokens
        store.shield.webDomainCategories = picked.categoryTokens.isEmpty ? nil : .specific(picked.categoryTokens)
    }

    static func schedule(from start: Date, to end: Date) -> DeviceActivitySchedule {
        let parts: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        return DeviceActivitySchedule(
            intervalStart: Calendar.current.dateComponents(parts, from: start),
            intervalEnd: Calendar.current.dateComponents(parts, from: end),
            repeats: false
        )
    }

    static var usageSchedule: DeviceActivitySchedule {
        DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: true
        )
    }

    static func restartUsage() throws {
        let center = DeviceActivityCenter()
        center.stopMonitoring([usage, cooldown])
        clearShield()
        defaults.removeObject(forKey: "unlockAt")
        guard enabled else { return }
        let picked = selection
        let event = DeviceActivityEvent(
            applications: picked.applicationTokens,
            categories: picked.categoryTokens,
            webDomains: picked.webDomainTokens,
            threshold: DateComponents(minute: workMinutes)
        )
        try center.startMonitoring(usage, during: usageSchedule, events: [limit: event])
    }

    static func beginRest() throws {
        guard enabled, unlockAt == nil else { return }
        let center = DeviceActivityCenter()
        let now = Date()
        let end = now.addingTimeInterval(TimeInterval(restMinutes * 60))
        try center.startMonitoring(cooldown, during: schedule(from: now, to: end), events: [:])
        center.stopMonitoring([usage])
        defaults.set(end, forKey: "unlockAt")
        shieldSelected()
    }

    static func stop() {
        defaults.set(false, forKey: "enabled")
        DeviceActivityCenter().stopMonitoring([usage, cooldown])
        defaults.removeObject(forKey: "unlockAt")
        clearShield()
    }
}
