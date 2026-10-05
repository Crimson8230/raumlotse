package at.mci.igp.raumlotse.service;

import java.time.Instant;

public interface BookingConfirmationMailGateway {
    void send(Command command);

    record Command(String recipient, String roomName, Instant startTime, Instant endTime) {
        public Command {
            if (recipient == null || recipient.isBlank() || roomName == null || roomName.isBlank()
                    || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
                throw new IllegalArgumentException("Complete delivery data is required.");
            }
        }
    }

    class MessageFormatException extends RuntimeException {
        public MessageFormatException(Throwable cause) { super(cause); }
    }

    class SubmissionException extends RuntimeException {
        public SubmissionException(Throwable cause) { super(cause); }
    }
}
