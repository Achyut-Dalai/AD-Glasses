import EventKit
import Foundation

public struct CalendarEventInfo: Equatable, Sendable {
    public let id: String
    public let title: String
    public let startDate: Date
    public let endDate: Date
    public let isAllDay: Bool
    public let location: String?

    public init(id: String, title: String, startDate: Date, endDate: Date, isAllDay: Bool, location: String?) {
        self.id = id
        self.title = title
        self.startDate = startDate
        self.endDate = endDate
        self.isAllDay = isAllDay
        self.location = location
    }
}

public enum CalendarQueryResult: Equatable, Sendable {
    case foundEvent(event: CalendarEventInfo, proactiveReminderOffered: Bool)
    case foundMultiple(events: [CalendarEventInfo])
    case noEvents(query: String?)
    case permissionDenied(String)
}

public enum ReminderCreationResult: Equatable, Sendable {
    case created(title: String, dueDate: Date)
    case alreadyExists(title: String, dueDate: Date)
    case failed(String)
}

/// Manages Apple Calendar and Reminders via native `EventKit`.
/// Runs queries entirely on-device with zero network transmission.
public final class CalendarManager: @unchecked Sendable {
    public static let shared = CalendarManager()

    private let eventStore = EKEventStore()

    public init() {}

    // MARK: - Permissions

    public func requestCalendarAccess() async -> Bool {
        if #available(iOS 17.0, *) {
            do {
                return try await eventStore.requestFullAccessToEvents()
            } catch {
                return false
            }
        } else {
            do {
                return try await eventStore.requestAccess(to: .event)
            } catch {
                return false
            }
        }
    }

    public func requestRemindersAccess() async -> Bool {
        if #available(iOS 17.0, *) {
            do {
                return try await eventStore.requestFullAccessToReminders()
            } catch {
                return false
            }
        } else {
            do {
                return try await eventStore.requestAccess(to: .reminder)
            } catch {
                return false
            }
        }
    }

    // MARK: - Query Calendar

    /// Finds events matching an optional query (e.g. "movie", "dentist", "meeting", or nil for today's schedule).
    public func queryEvents(matching query: String?, on date: Date = Date()) async -> CalendarQueryResult {
        guard await requestCalendarAccess() else {
            return .permissionDenied("Calendar access is needed to check your events. Please grant Calendar access in iOS Settings.")
        }

        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: date)
        guard let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay) else {
            return .noEvents(query: query)
        }

        // Also search upcoming 7 days if looking for a specific keyword like "movie"
        let searchEnd: Date
        if let q = query, !q.isEmpty {
            searchEnd = calendar.date(byAdding: .day, value: 7, to: startOfDay) ?? endOfDay
        } else {
            searchEnd = endOfDay
        }

        let predicate = eventStore.predicateForEvents(withStart: startOfDay, end: searchEnd, calendars: nil)
        let events = eventStore.events(matching: predicate)

        if let query = query, !query.isEmpty {
            let normQuery = PhoneCallManager.normalizeName(query)
            let matching = events.filter { event in
                let normTitle = PhoneCallManager.normalizeName(event.title ?? "")
                if normTitle.contains(normQuery) || normQuery.contains(normTitle) {
                    return true
                }
                return PhoneCallManager.similarityRatio(normQuery, normTitle) >= 0.50
            }.sorted { $0.startDate < $1.startDate }

            guard let first = matching.first else {
                return .noEvents(query: query)
            }

            let info = CalendarEventInfo(
                id: first.eventIdentifier,
                title: first.title ?? "Event",
                startDate: first.startDate,
                endDate: first.endDate,
                isAllDay: first.isAllDay,
                location: first.location
            )

            if matching.count == 1 {
                let reminderAlreadySet = await hasActiveReminder(for: info.title)
                return .foundEvent(event: info, proactiveReminderOffered: !reminderAlreadySet)
            } else {
                let infos = matching.prefix(3).map {
                    CalendarEventInfo(
                        id: $0.eventIdentifier,
                        title: $0.title ?? "Event",
                        startDate: $0.startDate,
                        endDate: $0.endDate,
                        isAllDay: $0.isAllDay,
                        location: $0.location
                    )
                }
                return .foundMultiple(events: infos)
            }
        } else {
            // General schedule query (e.g. "what's on my schedule today")
            let todayEvents = events.filter { $0.startDate < endOfDay }.sorted { $0.startDate < $1.startDate }
            guard !todayEvents.isEmpty else {
                return .noEvents(query: nil)
            }
            let infos = todayEvents.map {
                CalendarEventInfo(
                    id: $0.eventIdentifier,
                    title: $0.title ?? "Event",
                    startDate: $0.startDate,
                    endDate: $0.endDate,
                    isAllDay: $0.isAllDay,
                    location: $0.location
                )
            }
            if infos.count == 1 {
                let reminderAlreadySet = await hasActiveReminder(for: infos[0].title)
                return .foundEvent(event: infos[0], proactiveReminderOffered: !reminderAlreadySet)
            } else {
                return .foundMultiple(events: infos)
            }
        }
    }

    // MARK: - Formatting Voice Responses

    /// Produces natural speech description of an event.
    public func formatEventSpokenDescription(_ event: CalendarEventInfo) -> String {
        let calendar = Calendar.current
        let timeFormatter = DateFormatter()
        timeFormatter.timeStyle = .short
        timeFormatter.dateStyle = .none

        let timeStr = timeFormatter.string(from: event.startDate)

        let dayStr: String
        if calendar.isDateInToday(event.startDate) {
            dayStr = "today"
        } else if calendar.isDateInTomorrow(event.startDate) {
            dayStr = "tomorrow"
        } else {
            let dayFormatter = DateFormatter()
            dayFormatter.dateFormat = "EEEE, MMMM d"
            dayStr = "on \(dayFormatter.string(from: event.startDate))"
        }

        var text = "\"\(event.title)\" is at \(timeStr) \(dayStr)"
        if let location = event.location, !location.isEmpty {
            text += " at \(location)"
        }
        return text
    }

    // MARK: - Reminders

    /// Creates an Apple Reminder with an alarm set relative to an event start date (e.g. 1 hour before).
        /// Checks whether an active, incomplete reminder exists for a given title or event.
    public func hasActiveReminder(for title: String) async -> Bool {
        guard await requestRemindersAccess() else { return false }
        let predicate = eventStore.predicateForIncompleteReminders(
            withDueDateStarting: nil,
            ending: nil,
            calendars: nil
        )
        let existingReminders: [EKReminder] = await withCheckedContinuation { continuation in
            eventStore.fetchReminders(matching: predicate) { list in
                continuation.resume(returning: list ?? [])
            }
        }
        let clean = title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return existingReminders.contains { reminder in
            guard let remTitle = reminder.title?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else { return false }
            return remTitle == clean || remTitle == "reminder: \(clean)" || remTitle.contains(clean) || clean.contains(remTitle)
        }
    }

    public func createReminder(
        title: String,
        targetDate: Date,
        offsetMinutes: Int = 60
    ) async -> ReminderCreationResult {
        guard await requestRemindersAccess() else {
            return .failed("Reminders access is needed to schedule this reminder. Please enable Reminders in iOS Settings.")
        }

        // Check if an active reminder already exists with this title
        let predicate = eventStore.predicateForIncompleteReminders(
            withDueDateStarting: nil,
            ending: nil,
            calendars: nil
        )
        let existingReminders: [EKReminder] = await withCheckedContinuation { continuation in
            eventStore.fetchReminders(matching: predicate) { list in
                continuation.resume(returning: list ?? [])
            }
        }
        let normTitle = title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let duplicate = existingReminders.first(where: {
            $0.title?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() == normTitle
        }) {
            let due = duplicate.alarms?.first?.absoluteDate ?? targetDate.addingTimeInterval(-Double(offsetMinutes * 60))
            return .alreadyExists(title: title, dueDate: due)
        }

        let reminder = EKReminder(eventStore: eventStore)
        reminder.title = title
        reminder.calendar = eventStore.defaultCalendarForNewReminders() ?? eventStore.calendars(for: .reminder).first

        let alarmDate = targetDate.addingTimeInterval(-Double(offsetMinutes * 60))
        let alarm = EKAlarm(absoluteDate: alarmDate)
        reminder.addAlarm(alarm)

        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: alarmDate)
        reminder.dueDateComponents = components

        do {
            try eventStore.save(reminder, commit: true)
            return .created(title: title, dueDate: alarmDate)
        } catch {
            return .failed(error.localizedDescription)
        }
    }
}
