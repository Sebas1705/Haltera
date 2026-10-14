import UIKit
import FirebaseCore
import GoogleSignIn
import ComposeApp

/// Google sign-in for the shared code (`GoogleSignInBridge`, Kotlin): the GoogleSignIn SDK is
/// Swift-first, so it lives here. Needs Google enabled in Firebase (client id in
/// GoogleService-Info.plist) and its reversed client id as a URL scheme (GOOGLE_REVERSED_CLIENT_ID
/// in project.yml); without both, `isConfigured` is false and the app hides Google.
final class GoogleSignInSwift: NSObject, GoogleSignInBridge {
    var isConfigured: Bool {
        guard let clientID = FirebaseApp.app()?.options.clientID else { return false }
        let scheme = clientID.components(separatedBy: ".").reversed().joined(separator: ".")
        let types = Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [[String: Any]] ?? []
        return types.contains { ($0["CFBundleURLSchemes"] as? [String])?.contains(scheme) == true }
    }

    func requestTokens(onResult: @escaping (String?, String?, String?) -> Void) {
        guard isConfigured,
              let clientID = FirebaseApp.app()?.options.clientID,
              let presenter = Self.topViewController() else {
            onResult(nil, nil, "Google sign-in is not configured")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError? {
                let cancelled = error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue
                onResult(nil, nil, cancelled ? nil : error.localizedDescription)
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                onResult(nil, nil, "Google returned no token")
                return
            }
            onResult(idToken, user.accessToken.tokenString, nil)
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
