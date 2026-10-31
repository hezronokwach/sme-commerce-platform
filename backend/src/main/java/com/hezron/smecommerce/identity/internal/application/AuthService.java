package com.hezron.smecommerce.identity.internal.application;

/** Account use cases. Login, refresh and logout join this interface in the next step. */
public interface AuthService {

    /**
     * Creates a shop and its OWNER together (workflow W1). Either both are saved or neither is.
     *
     * @throws EmailTakenException                                   if the email is already registered
     * @throws com.hezron.smecommerce.tenant.SlugTakenException      if the shop web name is taken
     * @throws com.hezron.smecommerce.tenant.SlugNotAllowedException if the web name is invalid or reserved
     */
    SignUpResult signUp(SignUpCommand command);

}
