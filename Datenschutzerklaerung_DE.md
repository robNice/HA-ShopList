
# Datenschutzerklärung

---
> 
> english version:  [Privacy Policy](Privacy_Policy_EN.md)
>
---

## 1. Allgemeines
Diese App dient ausschließlich als Einkaufsliste im Verbund mit Home Assistant.

Der Entwickler dieser App erhebt, speichert oder verarbeitet keine personenbezogenen Daten der Nutzer.

## 2. Netzwerkkommunikation
Die App kommuniziert ausschließlich mit dem vom Nutzer eingestellten Home-Assistant-Server.

Dabei werden lediglich technische Informationen übertragen, die für das Organisieren der Einkaufsliste erforderlich sind.

Diese Kommunikation erfolgt direkt zwischen der App und dem Home-Assistant-Server.
Abgesehen von der unten beschriebenen Update-Prüfung werden keine Daten an externe Server übertragen.

Ob die Verbindung verschlüsselt ist, hängt von der eingegebenen Adresse ab. Bei einer Adresse mit https:// werden alle Daten einschließlich des Authentifizierungs-Tokens verschlüsselt übertragen. Bei einer Adresse mit http:// erfolgt die Übertragung unverschlüsselt. Die App weist vor der Verwendung einer solchen Adresse ausdrücklich darauf hin. Für Verbindungen außerhalb des eigenen Heimnetzes wird HTTPS empfohlen.

### Update-Prüfung (nur GitHub-Version)
Die direkt über GitHub verteilte Version der App prüft regelmäßig, ob eine neue Version verfügbar ist. Dazu ruft sie die öffentliche Schnittstelle von GitHub (api.github.com) auf und lädt bei Bedarf die neue Version von GitHub herunter. Dabei erhält GitHub technisch bedingt die IP-Adresse des Geräts sowie übliche Verbindungsdaten. Es werden keine Home-Assistant-Daten, Tokens oder Einkaufslisten übertragen. Anbieter ist GitHub, Inc. (USA). Es gilt die Datenschutzerklärung von GitHub.

Die über Google Play installierte Version führt diese Prüfung nicht durch.

## 3. Datenspeicherung
Die App speichert keine personenbezogenen Daten.

Je nach Nutzung können lokal auf dem Gerät technische Einstellungen gespeichert werden, beispielsweise:

- die IP-Adresse des Home-Assistant-Servers
- das Authentifizierungs-Token
- die ausgewählte Einkaufsliste
- lokal gespeicherte Produktvorschläge für die Autovervollständigung

Diese Daten verbleiben ausschließlich auf dem Gerät des Nutzers.

## 4. Berechtigungen
Die App benötigt möglicherweise Netzwerk- und/oder Internetzugriff, je nachdem wo der Home-Assistant-Server verfügbar ist.

Es werden keine weiteren sensiblen Berechtigungen verwendet.

## 5. Drittanbieter
Diese App verwendet keine Drittanbieter-Dienste, keine Analyse-Tools und keine Werbung, mit Ausnahme der oben beschriebenen Update-Prüfung über GitHub in der GitHub-Version.

## 6. Änderungen dieser Datenschutzerklärung
Diese Datenschutzerklärung kann bei Bedarf aktualisiert werden, um Änderungen der App oder rechtliche Anforderungen abzubilden.

## 7. Kontakt
Bei Fragen zum Datenschutz können Sie den Entwickler kontaktieren.

E-Mail: ha-shoplist@robnice.de
