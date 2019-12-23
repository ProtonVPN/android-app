// ExternalCertificateProvider.aidl
package de.blinkt.openpvpn.api;


/*
 * This is very simple interface that is specialised to have only the minimal set of crypto
 * operation that are needed for OpenVPN to authenticate with an external certificate
 */
interface ExternalCertificateProvider {
    /**
     * Requests signing the data with RSA/ECB/PKCS1PADDING
     * for RSA certficate and with NONEwithECDSA for EC certificates
     * @parm alias the parameter that
     */
    byte[] getSignedData(in String alias, in byte[] data);

    /**
     * Requests the certificate chain for the selected alias
     * The first certifcate returned is assumed to be
     * the user certificate
     */
    byte[] getCertificateChain(in String alias);

    /**
     * This function is called for the app to get additional meta information from the
     * external provider and will be called with the stored alias in the app
     *
     * For external app provider that do not provide an activity to configure them, this
     * is used to get the alias that should be used.
     * The format is the same as the activity should return, i.e.
     *
     * EXTRA_ALIAS = "de.blinkt.openpvpn.api.KEY_ALIAS"
     * EXTRA_DESCRIPTION = "de.blinkt.openpvpn.api.KEY_DESCRIPTION"
     *
     * as the keys for the bundle.
     *
     */
    Bundle getCertificateMetaData(in String alias);
}
