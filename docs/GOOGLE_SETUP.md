# Activer Gmail dans SpaceSaver V3

La connexion réelle nécessite un projet Google Cloud appartenant au développeur. L’APK seul ne peut pas créer le consentement Google à ta place. Commencer avec un compte personnel de test et quelques messages sacrifiables.

## 1. Projet et API

Ouvrir [Google Cloud Console](https://console.cloud.google.com/), sélectionner ou créer un projet pour SpaceSaver. Dans **API et services → Bibliothèque**, rechercher **Gmail API** et l’activer.

## 2. Google Auth Platform

Configurer l’application dans **Google Auth Platform** : nom **SpaceSaver**, adresse d’assistance et coordonnées développeur. Pour un compte Gmail personnel, choisir une audience **Externe**, conserver le statut **Test** et ajouter l’adresse Gmail utilisée dans **Utilisateurs de test**.

Dans **Accès aux données**, ajouter uniquement le scope demandé par l’APK :

```text
https://www.googleapis.com/auth/gmail.modify
```

Ce scope est restreint. Il permet davantage d’actions que SpaceSaver n’en utilise ; l’app appelle uniquement la lecture de profil/métadonnées et la mise à la corbeille. Le mode test ne vaut pas validation pour une distribution publique. Ne pas passer en production pour contourner un refus d’accès.

## 3. Client OAuth Android

Dans **Clients → Créer un client**, choisir **Android**.

| Champ | Valeur |
| --- | --- |
| Nom | SpaceSaver Android test |
| Nom du package | `com.sachalou.spacesaver` |
| Empreinte SHA-1 | Valeur `Signer #1 certificate SHA-1 digest` dans `Google-OAuth-certificate.txt`, livré dans le même ZIP que l’APK |

L’empreinte doit correspondre exactement à l’APK installé. Ce fichier contient uniquement le certificat public et ses empreintes, jamais la clé privée.

Cette V3 utilise directement `AuthorizationClient`, sans serveur ni demande d’accès hors ligne : aucun client secret ni fichier `google-services.json` n’est à intégrer dans l’APK. Une signature différente exige l’enregistrement du nouveau SHA-1. Pour une diffusion durable, configurer une clé de signature stable dans les secrets de CI avant d’enregistrer le client définitif.

## 4. Essai sur téléphone

Installer l’APK correspondant, ouvrir **Gmail → Connecter Gmail**, choisir le compte ajouté aux utilisateurs de test et examiner les autorisations. Android doit disposer de Google Play services. Aucun mot de passe n’est saisi dans SpaceSaver.

L’adresse du compte apparaît dans l’app. Régler les filtres, puis lancer **Analyser sans rien supprimer**. La connexion ou l’analyse ne déplace aucun message. Pour le premier nettoyage, sélectionner seulement quelques messages sacrifiables, confirmer, puis vérifier et restaurer depuis la corbeille Gmail.

## Si la connexion échoue

- Code développeur / code 10 : vérifier le nom de package et le SHA-1 de l’APK installé.
- Accès refusé / HTTP 403 : vérifier API Gmail activée, utilisateur de test, scope accordé et éventuelles restrictions administrateur Workspace.
- Autorisation expirée / HTTP 401 : fermer la session locale et reconnecter le compte.
- Après une autre compilation : vérifier le certificat de ce nouvel APK. Le runner génère actuellement une clé debug propre à son environnement.

Les projets en test ont des limites et peuvent nécessiter un nouveau consentement. Avant distribution publique, mettre en place la signature définitive, les informations d’assistance, une politique de confidentialité adaptée et les vérifications Google applicables.

## Références officielles

- [Autorisation Android et client OAuth](https://developer.android.com/identity/authorization)
- [Scopes Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes)
- [Audience et utilisateurs de test](https://support.google.com/cloud/answer/15549945)
- [Scopes restreints et vérification](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification)
- [Stockage Google, corbeille incluse](https://support.google.com/mail/answer/9312312)
