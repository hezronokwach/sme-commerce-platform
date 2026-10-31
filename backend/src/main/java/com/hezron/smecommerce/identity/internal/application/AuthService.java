package com.hezron.smecommerce.identity.internal.application;

/** Account use cases for staff users. */
public interface AuthService {

    /**
     * Creates a shop and its OWNER together (workflow W1). Either both are saved or neither is.
     * The owner is logged in: the result carries a new token pair.
     *
     * @throws EmailTakenException                                   if the email is already registered
     * @throws com.hezron.smecommerce.tenant.SlugTakenException      if the shop web name is taken
     * @throws com.hezron.smecommerce.tenant.SlugNotAllowedException if the web name is invalid or reserved
     */
    SignUpResult signUp(SignUpCommand command);

    /**
     * Checks email and password and starts a new login session.
     *
     * @throws InvalidCredentialsException if the email is unknown, the password is wrong or the account isn't active
     * @throws TooManyAttemptsException    if the account is locked after too many wrong passwords
     */
    AuthTokens login(LoginCommand command);

    /**
     * Exchanges a refresh token for a new pair. Each refresh token works once.
     *
     * @throws InvalidRefreshTokenException if the token is missing, unknown, expired, revoked or reused
     */
    AuthTokens refresh(String refreshToken);

    /** Ends the login session of this refresh token. Does nothing for a missing or unknown token. */
    void logout(String refreshToken);

}
