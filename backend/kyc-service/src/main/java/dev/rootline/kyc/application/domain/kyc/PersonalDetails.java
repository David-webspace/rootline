package dev.rootline.kyc.application.domain.kyc;

import dev.rootline.kyc.application.domain.shared.DomainException;
import java.time.LocalDate;
import java.time.Period;

/** 實名資料：姓名、生日、居住地址。 */
public record PersonalDetails(String firstName, String lastName, LocalDate dateOfBirth, Address address) {

    public static final int MINIMUM_AGE = 18;

    public PersonalDetails {
        firstName = name(firstName, "First name");
        lastName = name(lastName, "Last name");
        if (dateOfBirth == null) {
            throw DomainException.invalid("DATE_OF_BIRTH_INVALID", "Date of birth is required");
        }
        if (dateOfBirth.isAfter(LocalDate.now()) || dateOfBirth.isBefore(LocalDate.of(1900, 1, 1))) {
            throw DomainException.invalid("DATE_OF_BIRTH_INVALID", "Date of birth is out of range");
        }
        if (address == null) {
            throw DomainException.invalid("ADDRESS_INVALID", "Residential address is required");
        }
    }

    public boolean isUnderage(LocalDate today) {
        return Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE;
    }

    private static String name(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 50) {
            throw DomainException.invalid("NAME_INVALID", label + " is required (max 50 chars)");
        }
        return trimmed;
    }

    public record Address(String countryCode, String state, String city, String postalCode, String detail) {

        public Address {
            countryCode = countryCode == null ? "" : countryCode.trim().toUpperCase(java.util.Locale.ROOT);
            if (!countryCode.matches("^[A-Z]{2}$")) {
                throw DomainException.invalid("ADDRESS_INVALID", "Address country must be an ISO alpha-2 code");
            }
            city = required(city, "City");
            detail = required(detail, "Address detail");
            state = state == null ? "" : state.trim();
            postalCode = postalCode == null ? "" : postalCode.trim();
        }

        private static String required(String value, String label) {
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.isEmpty() || trimmed.length() > 200) {
                throw DomainException.invalid("ADDRESS_INVALID", label + " is required (max 200 chars)");
            }
            return trimmed;
        }
    }
}
