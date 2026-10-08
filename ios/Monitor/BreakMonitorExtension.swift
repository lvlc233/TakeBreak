import DeviceActivity

final class BreakMonitorExtension: DeviceActivityMonitor {
    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name, activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        guard activity == BreakState.usage, event == BreakState.limit, BreakState.enabled else { return }
        do { try BreakState.beginRest() }
        catch { BreakState.clearShield() }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        guard activity == BreakState.cooldown else { return }
        do { try BreakState.restartUsage() }
        catch { BreakState.clearShield() }
    }
}
