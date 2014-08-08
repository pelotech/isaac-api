package uk.ac.cam.cl.dtg.segue.dos.users;

import java.util.Date;

/**
 * Data Object to represent a user of a 3rd party provider. This object will NOT 
 * be persisted in the database.
 * 
 */
public class UserFromAuthProvider {
	private String providerUserId;
	private String givenName;
	private String familyName;
	private String email;
	private Date dateOfBirth;
	private Gender gender;
	
	/**
	 * Full constructor for the User object.
	 * 
	 * @param providerUserId
	 *            - Our database Unique ID
	 * @param givenName
	 *            - Equivalent to firstname
	 * @param familyName
	 *            - Equivalent to second name
	 * @param email
	 *            - primary e-mail address
	 * @param role
	 *            - role description
	 * @param dateOfBirth
	 *            - date of birth to help with monitoring
	 * @param gender
	 *            - gender of the user
	 */
	public UserFromAuthProvider(
			final String providerUserId,
			final String givenName,
			final String familyName,
			final String email,
			final Role role,
			final Date dateOfBirth,
			final Gender gender) {
		this.providerUserId = providerUserId;
		this.familyName = familyName;
		this.givenName = givenName;
		this.email = email;
		this.dateOfBirth = dateOfBirth;
		this.gender = gender;
	}

	/**
	 * Gets the providerId.
	 * @return the providerId
	 */
	public String getProviderUserId() {
		return providerUserId;
	}

	/**
	 * Gets the familyName.
	 * @return the familyName
	 */
	public String getFamilyName() {
		return familyName;
	}

	/**
	 * Gets the givenName.
	 * @return the givenName
	 */
	public String getGivenName() {
		return givenName;
	}

	/**
	 * Gets the email.
	 * @return the email
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * Gets the dateOfBirth.
	 * @return the dateOfBirth
	 */
	public Date getDateOfBirth() {
		return dateOfBirth;
	}

	/**
	 * Gets the gender.
	 * @return the gender
	 */
	public Gender getGender() {
		return gender;
	}
}