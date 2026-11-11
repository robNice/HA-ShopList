# Privacy Policy

---
>
> deutsche Version:  [Datenschutzerklärung](Datenschutzerklaerung_DE.md)
>
---

## 1. General
This app is intended solely for use as a shopping list in combination with Home Assistant.

The developer of this app does not collect, store, or process any personal data of users.

## 2. Network Communication
The app communicates exclusively with the Home Assistant server configured by the user.

Only technical information required for organizing the shopping list is transmitted.

This communication takes place directly between the app and the Home Assistant server.
Apart from the update check described below, no data is transmitted to external servers.

Whether the connection is encrypted depends on the address entered. With an address starting with https://, all data including the authentication token is transmitted encrypted. With an address starting with http://, data is transmitted unencrypted. The app explicitly warns the user before such an address is used. HTTPS is recommended for connections outside your own home network.

### Update Check (GitHub Version Only)
The version of the app distributed directly via GitHub regularly checks whether a new version is available. To do so, it calls the public GitHub interface (api.github.com) and, if needed, downloads the new version from GitHub. For technical reasons, GitHub receives the device's IP address and common connection data. No Home Assistant data, tokens, or shopping lists are transmitted. The provider is GitHub, Inc. (USA). GitHub's privacy policy applies.

The version installed via Google Play does not perform this check.

## 3. Data Storage
The app does not store any personal data.

Depending on usage, technical settings may be stored locally on the device, for example:

- the IP address of the Home Assistant server
- the authentication token
- the selected shopping list
- locally stored product suggestions for autocomplete

This data remains exclusively on the user's device.

## 4. Permissions
The app may require network and/or internet access, depending on where the Home Assistant server is available.

No other sensitive permissions are used.

## 5. Third Parties
This app does not use any third-party services, analytics tools, or advertising, except for the update check via GitHub in the GitHub version described above.

## 6. Changes to This Privacy Policy
This privacy policy may be updated if necessary to reflect changes to the app or legal requirements.

## 7. Contact
If you have any questions about data protection, you can contact the developer.

Email: ha-shoplist@robnice.de
