import UserNotifications
import Foundation
@preconcurrency import Contacts
import UIKit

public struct ContactCallTarget: Equatable, Sendable {
    public let displayName: String
    public let phoneNumber: String
    public let label: String?

    public init(displayName: String, phoneNumber: String, label: String? = nil) {
        self.displayName = displayName
        self.phoneNumber = phoneNumber
        self.label = label
    }
}

public enum PhoneCallResolutionResult: Equatable, Sendable {
    /// Exactly one unambiguous match found with a valid phone number.
    case matched(ContactCallTarget)
    /// Multiple contacts matched (2 to 4 candidates); prompt the user with disambiguation options.
    case ambiguous(query: String, candidates: [ContactCallTarget])
    /// Only a low-confidence/faint match was found; ask the user to clarify to avoid dialing the wrong person.
    case lowConfidence(query: String, suggestion: ContactCallTarget)
    /// No matching contact was found in the address book.
    case notFound(query: String)
}

public enum PhoneCallError: LocalizedError, Equatable, Sendable {
    case permissionDenied
    case contactNotFound(String)
    case noPhoneNumbers(String)
    case invalidPhoneNumber(String)
    case dialingFailed(String)

    public var errorDescription: String? {
        switch self {
        case .permissionDenied:
            return "Contacts access is required to find phone numbers. Please allow Contacts permission in iOS Settings."
        case .contactNotFound(let name):
            return "Could not find a contact matching '\(name)'."
        case .noPhoneNumbers(let name):
            return "Contact '\(name)' has no phone numbers listed."
        case .invalidPhoneNumber(let number):
            return "'\(number)' is not a valid phone number."
        case .dialingFailed(let number):
            return "Unable to place call to \(number)."
        }
    }
}

/// Manages querying contacts and placing phone calls via the system `tel:` scheme.
/// When triggered while wearing AD Glasses, iOS automatically routes the outbound call
/// audio through the glasses via Classic Bluetooth (HFP/Hands-Free Profile).
final class PhoneCallManager: @unchecked Sendable {
    public static let shared = PhoneCallManager()

    private let contactStore = CNContactStore()

    public init() {}

    // MARK: - Lock Screen Actionable Notifications

    public static let callNotificationCategoryIdentifier = "AD_GLASSES_CALL_CATEGORY"
    public static let callActionIdentifier = "AD_GLASSES_CALL_ACTION"
    public static let callAction1Identifier = "AD_GLASSES_CALL_ACTION_1"
    public static let callAction2Identifier = "AD_GLASSES_CALL_ACTION_2"
    public static let callAction3Identifier = "AD_GLASSES_CALL_ACTION_3"
    public static let dismissActionIdentifier = "AD_GLASSES_DISMISS_ACTION"

    public static let callMultiCategoryIdentifier = "AD_GLASSES_CALL_MULTI_CATEGORY"
    public static let smsMultiCategoryIdentifier = "AD_GLASSES_SMS_MULTI_CATEGORY"

    public static let smsNotificationCategoryIdentifier = "AD_GLASSES_SMS_CATEGORY"
    public static let smsActionIdentifier = "AD_GLASSES_SMS_ACTION"
    public static let smsAction1Identifier = "AD_GLASSES_SMS_ACTION_1"
    public static let smsAction2Identifier = "AD_GLASSES_SMS_ACTION_2"
    public static let smsAction3Identifier = "AD_GLASSES_SMS_ACTION_3"

    /// Registers the lock-screen notification categories and action buttons with iOS
    public func registerNotificationCategories() {
        let dismissAction = UNNotificationAction(
            identifier: Self.dismissActionIdentifier,
            title: "Dismiss",
            options: [.destructive]
        )

        // Single number call category
        let callAction = UNNotificationAction(
            identifier: Self.callActionIdentifier,
            title: "📞 Call Now",
            options: []
        )
        let callCategory = UNNotificationCategory(
            identifier: Self.callNotificationCategoryIdentifier,
            actions: [callAction, dismissAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )

        // Multiple numbers call category: displays each number button + 1 dismiss button
        let call1 = UNNotificationAction(identifier: Self.callAction1Identifier, title: "📞 Call Number 1", options: [])
        let call2 = UNNotificationAction(identifier: Self.callAction2Identifier, title: "📞 Call Number 2", options: [])
        let call3 = UNNotificationAction(identifier: Self.callAction3Identifier, title: "📞 Call Number 3", options: [])
        let multiCallCategory = UNNotificationCategory(
            identifier: Self.callMultiCategoryIdentifier,
            actions: [call1, call2, call3, dismissAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )

        let smsAction = UNNotificationAction(
            identifier: Self.smsActionIdentifier,
            title: "💬 Send Message",
            options: [.foreground]
        )
        let smsCategory = UNNotificationCategory(
            identifier: Self.smsNotificationCategoryIdentifier,
            actions: [smsAction],
            intentIdentifiers: [],
            options: []
        )

        let sms1 = UNNotificationAction(identifier: Self.smsAction1Identifier, title: "💬 Text Number 1", options: [.foreground])
        let sms2 = UNNotificationAction(identifier: Self.smsAction2Identifier, title: "💬 Text Number 2", options: [.foreground])
        let sms3 = UNNotificationAction(identifier: Self.smsAction3Identifier, title: "💬 Text Number 3", options: [.foreground])
        let multiSMSCategory = UNNotificationCategory(
            identifier: Self.smsMultiCategoryIdentifier,
            actions: [sms1, sms2, sms3, dismissAction],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )

        UNUserNotificationCenter.current().setNotificationCategories([
            callCategory, multiCallCategory, smsCategory, multiSMSCategory
        ])
    }

    /// Requests notification permissions if not already granted
    public func requestNotificationPermissionIfNeeded() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }


    /// Posts a lock-screen notification showing all numbers of the contact with action buttons and 1 dismiss button
    public func postLockScreenMultiCallNotification(contactName: String, targets: [ContactCallTarget]) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            let content = UNMutableNotificationContent()
            content.title = "📞 Call \(contactName)"
            
            let bulletList = targets.prefix(3).enumerated().map { idx, t in
                let label = t.label != nil ? "(\(t.label!)) " : ""
                return "\(idx + 1). \(label)\(Self.sanitizePhoneNumber(t.phoneNumber))"
            }.joined(separator: "\n")
            
            content.body = "Select which number to call:\n\(bulletList)"
            content.sound = .default
            content.interruptionLevel = .timeSensitive
            content.categoryIdentifier = Self.callMultiCategoryIdentifier

            var userInfo: [String: Any] = [
                "displayName": contactName,
                "count": targets.count
            ]
            for (idx, t) in targets.prefix(3).enumerated() {
                userInfo["number_\(idx + 1)"] = Self.sanitizePhoneNumber(t.phoneNumber)
                userInfo["label_\(idx + 1)"] = t.label ?? "Number \(idx + 1)"
            }
            content.userInfo = userInfo

            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
            let request = UNNotificationRequest(identifier: "AD_GLASSES_MULTI_CALL_\(UUID().uuidString)", content: content, trigger: trigger)
            UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
        }
    }

    /// Posts a lock-screen notification showing all numbers of the contact for SMS
    public func postLockScreenMultiSMSNotification(contactName: String, targets: [ContactCallTarget], body: String) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            let content = UNMutableNotificationContent()
            content.title = "💬 Text \(contactName)"
            
            let bulletList = targets.prefix(3).enumerated().map { idx, t in
                let label = t.label != nil ? "(\(t.label!)) " : ""
                return "\(idx + 1). \(label)\(Self.sanitizePhoneNumber(t.phoneNumber))"
            }.joined(separator: "\n")
            
            content.body = "\"\(body)\"\nSelect recipient number:\n\(bulletList)"
            content.sound = .default
            content.categoryIdentifier = Self.smsMultiCategoryIdentifier

            var userInfo: [String: Any] = [
                "displayName": contactName,
                "messageBody": body,
                "count": targets.count
            ]
            for (idx, t) in targets.prefix(3).enumerated() {
                userInfo["number_\(idx + 1)"] = Self.sanitizePhoneNumber(t.phoneNumber)
                userInfo["label_\(idx + 1)"] = t.label ?? "Number \(idx + 1)"
            }
            content.userInfo = userInfo

            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
            let request = UNNotificationRequest(identifier: "AD_GLASSES_MULTI_SMS_\(UUID().uuidString)", content: content, trigger: trigger)
            UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
        }
    }

    /// Posts a lock-screen interactive banner if direct dialing was suppressed or phone is locked
    public func postLockScreenCallNotification(for target: ContactCallTarget) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            let content = UNMutableNotificationContent()
            content.title = "📞 Call \(target.displayName)"
            let sanitized = Self.sanitizePhoneNumber(target.phoneNumber)
            content.body = "Tap to call \(target.displayName) (\(sanitized))."
            content.sound = .default
            content.interruptionLevel = .timeSensitive
            content.categoryIdentifier = Self.callNotificationCategoryIdentifier
            content.userInfo = ["phoneNumber": sanitized, "displayName": target.displayName]

            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
            let request = UNNotificationRequest(identifier: "AD_GLASSES_CALL_\(UUID().uuidString)", content: content, trigger: trigger)
            UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
        }
    }

    /// Posts a lock-screen interactive banner for SMS
    public func postLockScreenSMSNotification(for target: ContactCallTarget, body: String) {
        let content = UNMutableNotificationContent()
        content.title = "💬 Text \(target.displayName)"
        content.body = "\"\(body)\" — Tap to open and send."
        content.sound = .default
        content.categoryIdentifier = Self.smsNotificationCategoryIdentifier
        content.userInfo = [
            "phoneNumber": Self.sanitizePhoneNumber(target.phoneNumber),
            "displayName": target.displayName,
            "messageBody": body
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let request = UNNotificationRequest(identifier: "AD_GLASSES_SMS_\(UUID().uuidString)", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }


    /// Checks or requests access to the user's contacts.
    public func requestContactsAccess() async -> Bool {
        let status = CNContactStore.authorizationStatus(for: .contacts)
        switch status {
        case .authorized, .limited:
            return true
        case .notDetermined:
            do {
                return try await contactStore.requestAccess(for: .contacts)
            } catch {
                return false
            }
        case .denied, .restricted:
            return false
        @unknown default:
            return false
        }
    }

    /// Normalizes a name string for fuzzy phonetics comparison (lowercased, punctuation removed).
    static func normalizeName(_ text: String) -> String {
        text.lowercased()
            .folding(options: [.diacriticInsensitive, .caseInsensitive, .widthInsensitive], locale: .current)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    /// Computes Levenshtein distance between two strings.
    static func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let a = Array(s1)
        let b = Array(s2)
        if a.isEmpty { return b.count }
        if b.isEmpty { return a.count }

        var row = Array(0...b.count)
        for (i, ca) in a.enumerated() {
            var newRow = [i + 1] + Array(repeating: 0, count: b.count)
            for (j, cb) in b.enumerated() {
                let cost = ca == cb ? 0 : 1
                newRow[j + 1] = min(
                    row[j + 1] + 1,      // deletion
                    newRow[j] + 1,       // insertion
                    row[j] + cost        // substitution
                )
            }
            row = newRow
        }
        return row[b.count]
    }

    /// Computes similarity ratio between 0.0 (completely different) and 1.0 (exact match).
    static func similarityRatio(_ s1: String, _ s2: String) -> Double {
        let n1 = normalizeName(s1)
        let n2 = normalizeName(s2)
        if n1 == n2 { return 1.0 }
        let maxLen = max(n1.count, n2.count)
        guard maxLen > 0 else { return 1.0 }
        let dist = levenshteinDistance(n1, n2)
        return max(0.0, 1.0 - (Double(dist) / Double(maxLen)))
    }

    /// Sanitizes phone number digits and preserves leading plus sign for international formatting.
    static func sanitizePhoneNumber(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        var sanitized = ""
        for char in trimmed {
            if char == "+" && sanitized.isEmpty {
                sanitized.append(char)
            } else if char.isNumber {
                sanitized.append(char)
            }
        }
        return sanitized
    }

    /// Resolves the call target with intelligent disambiguation and confidence scoring.
    public func resolveCallTarget(for query: String) async throws -> PhoneCallResolutionResult {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else {
            return .notFound(query: query)
        }

        // Direct phone number dialing (if query consists primarily of digits, +, -, (, ), spaces)
        let digitCount = clean.filter(\.isNumber).count
        let alphaCount = clean.filter(\.isLetter).count
        if digitCount >= 3 && alphaCount == 0 {
            let sanitized = Self.sanitizePhoneNumber(clean)
            return .matched(ContactCallTarget(displayName: clean, phoneNumber: sanitized, label: nil))
        }

        guard await requestContactsAccess() else {
            throw PhoneCallError.permissionDenied
        }

        let keysToFetch: [CNKeyDescriptor] = [
            CNContactGivenNameKey as CNKeyDescriptor,
            CNContactFamilyNameKey as CNKeyDescriptor,
            CNContactNicknameKey as CNKeyDescriptor,
            CNContactOrganizationNameKey as CNKeyDescriptor,
            CNContactPhoneNumbersKey as CNKeyDescriptor
        ]

        // 1. Fetch matching contacts using Apple's native predicate
        let predicate = CNContact.predicateForContacts(matchingName: clean)
        var contacts: [CNContact]
        do {
            contacts = try contactStore.unifiedContacts(matching: predicate, keysToFetch: keysToFetch)
        } catch {
            contacts = []
        }

        // 2. If predicate yielded no matches, search all contacts to handle acoustic misspellings
        if contacts.isEmpty {
            let fetchRequest = CNContactFetchRequest(keysToFetch: keysToFetch)
            var allWithPhones = [CNContact]()
            do {
                try contactStore.enumerateContacts(with: fetchRequest) { contact, stop in
                    if !contact.phoneNumbers.isEmpty {
                        allWithPhones.append(contact)
                    }
                    if allWithPhones.count >= 200 {
                        stop.pointee = true
                    }
                }
            } catch {}
            contacts = allWithPhones
        }

        let contactsWithPhones = contacts.filter { !$0.phoneNumbers.isEmpty }
        guard !contactsWithPhones.isEmpty else {
            return .notFound(query: clean)
        }

        // 3. Score candidates based on query similarity
        let normQuery = Self.normalizeName(clean)
        struct ScoredCandidate {
            let target: ContactCallTarget
            let score: Double
            let isExact: Bool
        }

        var scored = [ScoredCandidate]()

        for contact in contactsWithPhones {
            let fullName = [contact.givenName, contact.familyName]
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            let displayName = fullName.isEmpty
                ? (contact.organizationName.isEmpty ? (contact.nickname.isEmpty ? clean : contact.nickname) : contact.organizationName)
                : fullName

            let normDisplay = Self.normalizeName(displayName)
            let normGiven = Self.normalizeName(contact.givenName)
            let normFamily = Self.normalizeName(contact.familyName)
            let normNick = Self.normalizeName(contact.nickname)

            // Exact match test
            let isExact = (normDisplay == normQuery) || (normGiven == normQuery) || (normFamily == normQuery) || (normNick == normQuery)

            // Calculate best similarity against full name, given name, family name, or nickname
            var maxSim = Self.similarityRatio(normQuery, normDisplay)
            if !normGiven.isEmpty { maxSim = max(maxSim, Self.similarityRatio(normQuery, normGiven)) }
            if !normFamily.isEmpty { maxSim = max(maxSim, Self.similarityRatio(normQuery, normFamily)) }
            if !normNick.isEmpty { maxSim = max(maxSim, Self.similarityRatio(normQuery, normNick)) }

            // Substring boost: e.g. user says "John" and contact is "John Smith"
            if normDisplay.contains(normQuery) || (!normGiven.isEmpty && normQuery.contains(normGiven)) {
                maxSim = max(maxSim, 0.88)
            }

            // Create targets for each phone number under this contact
            for phone in contact.phoneNumbers {
                let rawNumber = phone.value.stringValue
                let label = phone.label.flatMap { CNLabeledValue<NSString>.localizedString(forLabel: $0) }
                let target = ContactCallTarget(displayName: displayName, phoneNumber: rawNumber, label: label)
                scored.append(ScoredCandidate(target: target, score: maxSim, isExact: isExact))
            }
        }

        // Sort descending by score
        scored.sort { $0.score > $1.score }

        // Filter out candidates with very low similarity (< 0.50)
        let viable = scored.filter { $0.score >= 0.50 }
        guard let best = viable.first else {
            return .notFound(query: clean)
        }

        // Case A: Exact or near-perfect match
        if best.isExact || best.score >= 0.92 {
            // Check if there are different people with identical high scores (e.g. two different people named "John")
            let exactTies = viable.filter { $0.score >= 0.85 && $0.target.displayName != best.target.displayName }
            if !exactTies.isEmpty {
                let candidates = Array(viable.prefix(3).map(\.target))
                return .ambiguous(query: clean, candidates: candidates)
            }
            // Return best match directly without spoken disambiguation loop
            return .matched(best.target)
        }
        // Case B: Close tie between top 2 or 3 candidates (e.g. "Jon" matches "John", "Joan")
        if viable.count >= 2 {
            let second = viable[1]
            if abs(best.score - second.score) < 0.18 {
                let candidates = Array(viable.prefix(3).map(\.target))
                return .ambiguous(query: clean, candidates: candidates)
            }
        }

        // Case C: Single good match (>= 0.70)
        if best.score >= 0.70 {
            return .matched(best.target)
        }

        // Case D: Faint/Acoustic match (0.50 ..< 0.70) -> Do not dial automatically, ask for confirmation!
        return .lowConfidence(query: clean, suggestion: best.target)
    }

    /// Initiates a phone call via the RFC-compliant `tel:` or `telprompt:` URL scheme.
    /// iOS presents a system confirmation before dialing, which is required for outbound calls.
    @MainActor
    public func initiateCall(to target: ContactCallTarget) throws {
        let sanitized = Self.sanitizePhoneNumber(target.phoneNumber)
        guard !sanitized.isEmpty else {
            throw PhoneCallError.invalidPhoneNumber(target.phoneNumber)
        }

        // Standard RFC 3966 format for iOS Telephony is `tel:<digits>`
        guard let telURL = URL(string: "tel:\(sanitized)") else {
            throw PhoneCallError.invalidPhoneNumber(target.phoneNumber)
        }

        if UIApplication.shared.applicationState != .active {
            // When phone is locked / in background, post the Lock Screen banner directly so user can tap Call Now!
            self.postLockScreenCallNotification(for: target)
        } else {
            UIApplication.shared.open(telURL, options: [:]) { [weak self] success in
                if !success {
                    if let promptURL = URL(string: "telprompt:\(sanitized)") {
                        UIApplication.shared.open(promptURL, options: [:]) { promptSuccess in
                            if !promptSuccess {
                                self?.postLockScreenCallNotification(for: target)
                            }
                        }
                    } else {
                        self?.postLockScreenCallNotification(for: target)
                    }
                }
            }
        }
    }

    /// Initiates an SMS/iMessage via the system `sms:` URL scheme with recipient and body pre-filled.
    @MainActor
    public func initiateMessage(to target: ContactCallTarget, body: String) throws {
        let sanitized = Self.sanitizePhoneNumber(target.phoneNumber)
        guard !sanitized.isEmpty else {
            throw PhoneCallError.invalidPhoneNumber(target.phoneNumber)
        }

        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "+&?#")
        let encodedBody = body.addingPercentEncoding(withAllowedCharacters: allowed) ?? body

        // Apple iOS supports sms:<number>&body=<message>
        let urlString = "sms:\(sanitized)&body=\(encodedBody)"
        guard let smsURL = URL(string: urlString) else {
            throw PhoneCallError.invalidPhoneNumber(target.phoneNumber)
        }

        UIApplication.shared.open(smsURL, options: [:]) { [weak self] success in
            if !success {
                self?.postLockScreenSMSNotification(for: target, body: body)
            }
        }
    }
}

