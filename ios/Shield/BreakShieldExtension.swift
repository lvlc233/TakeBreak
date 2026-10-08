import ManagedSettings
import ManagedSettingsUI
import UIKit

final class BreakShieldExtension: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration { configuration() }
    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration { configuration() }
    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration { configuration() }
    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration { configuration() }

    private func configuration() -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemMaterial,
            backgroundColor: UIColor(red: 0.94, green: 0.95, blue: 0.89, alpha: 1),
            icon: UIImage(systemName: "leaf"),
            title: .init(text: "先歇一会儿", color: .darkGray),
            subtitle: .init(text: "去喝水、走走。休息结束会自动解锁；也可打开「歇一会儿」写一段话提前解锁。", color: .darkGray),
            primaryButtonLabel: .init(text: "知道了", color: .white),
            primaryButtonBackgroundColor: .systemGreen,
            secondaryButtonLabel: nil
        )
    }
}
