//
//  Feedback.swift
//  808Synth
//
//  "Send feedback": an email to Tuesday Games with the details that help
//  (app version, iOS version, device, whether the full version is owned)
//  already filled in. The person sees everything and chooses to send it;
//  the app itself sends nothing.
//
//  Uses Mail when it's set up, otherwise a mailto: link (opens the default
//  mail app, e.g. Gmail), otherwise shows the address to copy.
//

import MessageUI
import SwiftUI
import UIKit

enum Feedback {
    static let address = "info@tuesdaygames.com"
    static let subject = "808Synth feedback"

    /// The device's model code, e.g. "iPhone17,3" (Apple's name for the hardware).
    static var deviceModel: String {
        var info = utsname()
        uname(&info)
        let code = withUnsafeBytes(of: &info.machine) { raw in
            String(decoding: raw.prefix { $0 != 0 }, as: UTF8.self)
        }
        // The simulator reports the Mac's chip; ask for the simulated model instead.
        return ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] ?? code
    }

    static var appVersion: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }

    /// The email body: room to write at the top, details for us underneath.
    static func body(fullVersion: Bool) -> String {
        """


        ———
        Write your message above. These details help us help you:
        808Synth \(appVersion)
        \(UIDevice.current.systemName) \(UIDevice.current.systemVersion) · \(deviceModel)
        Full version: \(fullVersion ? "yes" : "no")
        """
    }

    static func mailtoURL(fullVersion: Bool) -> URL? {
        var parts = URLComponents()
        parts.scheme = "mailto"
        parts.path = address
        parts.queryItems = [
            URLQueryItem(name: "subject", value: subject),
            URLQueryItem(name: "body", value: body(fullVersion: fullVersion)),
        ]
        return parts.url
    }
}

/// Apple's mail sheet, filled in and ready to send.
struct MailComposer: UIViewControllerRepresentable {
    let fullVersion: Bool
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let mail = MFMailComposeViewController()
        mail.mailComposeDelegate = context.coordinator
        mail.setToRecipients([Feedback.address])
        mail.setSubject(Feedback.subject)
        mail.setMessageBody(Feedback.body(fullVersion: fullVersion), isHTML: false)
        return mail
    }

    func updateUIViewController(_ controller: MFMailComposeViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(dismiss: dismiss) }

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        let dismiss: DismissAction
        init(dismiss: DismissAction) { self.dismiss = dismiss }

        func mailComposeController(_ controller: MFMailComposeViewController,
                                   didFinishWith result: MFMailComposeResult, error: Error?) {
            dismiss()
        }
    }
}

/// The feedback row in the More sheet.
struct FeedbackSection: View {
    @Environment(\.openURL) private var openURL
    @State private var showMail = false
    @State private var showAddress = false

    var body: some View {
        Section {
            Button {
                send()
            } label: {
                Label("Send feedback", systemImage: "envelope.fill")
                    .font(.headline)
            }
            Text("Ideas, bugs, a beat you're proud of? We read everything.")
                .font(.footnote)
                .foregroundStyle(Theme.label)
        } header: {
            Text("Feedback")
        }
        .listRowBackground(Theme.module)
        .sheet(isPresented: $showMail) {
            MailComposer(fullVersion: Store.shared.isUnlocked)
                .ignoresSafeArea()
        }
        .alert("Email us", isPresented: $showAddress) {
            Button("Copy Address") { UIPasteboard.general.string = Feedback.address }
            Button("OK", role: .cancel) {}
        } message: {
            Text("Send your feedback to \(Feedback.address). It helps to mention your device and what happened.")
        }
    }

    private func send() {
        let owned = Store.shared.isUnlocked
        if MFMailComposeViewController.canSendMail() {
            showMail = true
        } else if let url = Feedback.mailtoURL(fullVersion: owned) {
            openURL(url) { opened in
                if !opened { showAddress = true }
            }
        } else {
            showAddress = true
        }
    }
}
