// ExternalCertificateProvider.aidl
package de.blinkt.openvpn.api;

/*
 * This is very simple interface that is specialised to have only the minimal set of crypto
 * operation that are needed for OpenVPN to authenticate with an external certificate
 */
interface ExternalCertificateProvider {
    /**
     * @deprecated use {@link #getSignedDataWithExtra} instead
     * Requests signing the data with RSA/ECB/PKCS1PADDING
     * for RSA certficate and with NONEwithECDSA for EC certificates
     * @param alias user certificate identifier
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
     * EXTRA_ALIAS = "de.blinkt.openvpn.api.KEY_ALIAS"
     * EXTRA_DESCRIPTION = "de.blinkt.openvpn.api.KEY_DESCRIPTION"
     *
     * as the keys for the bundle.
     *
     */
    Bundle getCertificateMetaData(in String alias);

    /**
     * Requests signing the data with RSA/ECB/PKCS1PADDING or RSA/ECB/nopadding
     * for RSA certficate and with NONEwithECDSA for EC certificates
     * @param alias user certificate identifier
     * @param data the data to be signed
     * @param extra additional information.
     * Should contain the following keys:
     * <p><ul>
       * <li>int key "de.blinkt.openvpn.api.RSA_PADDING_TYPE", may be set as:
       * <p><ul>
         * <li>0 - for RSA/ECB/nopadding
         * <li>1 - for RSA/ECB/PKCS1PADDING
         * </ul><p>
       * </ul><p>
     */
    byte[] getSignedDataWithExtra(in String alias, in byte[] data, in Bundle extra);
}
