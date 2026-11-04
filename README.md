# SpaceSaver · V2

**Stop paying to store digital junk.**

Application Android native Kotlin / Jetpack Compose, interface française. Version de test **0.2.0**, Android 8.0 minimum. Identifier les données inutiles avant de payer davantage de stockage.

## Gmail

- Autorisation Google native, compte utilisé visible avant toute opération.
- Promotions et/ou Réseaux sociaux de plus de **1 à 30 ans**, au choix.
- Tous les mails de plus de **1 à 30 ans**, avec un âge indépendant.
- Combinaison des règles par **OU** : promotions/réseaux de plus de 2 ans, ou tout mail de plus de 10 ans, par exemple.
- Favoris et messages importants conservés par défaut ; option pour préserver les non lus. Messages envoyés, brouillons, spams et mails déjà à la corbeille toujours exclus.
- Aperçu par pages de 100 candidats, au maximum 500 messages examinés par analyse. Objet, expéditeur, date et taille estimée. Sélection vide au départ.
- Confirmation du compte et de la sélection avant mise à la corbeille. Règles revérifiées sur les métadonnées actuelles de chaque message. Aucun traitement de conversations entières.
- Arrêt entre deux messages et bilan des opérations partielles. Après nettoyage, relancer l’analyse pour les lots suivants.

**L’API Gmail et le client OAuth Android doivent être configurés dans ton projet Google Cloud avant la première connexion.** Voir [la procédure](docs/GOOGLE_SETUP.md). Aucun secret OAuth ne doit être ajouté au code.

La taille affichée estime les messages chargés : ce n’est ni le quota Google global ni un gain confirmé. Les mails dans la corbeille comptent toujours dans le stockage. La V2 ne vide pas automatiquement la corbeille. Restauration et suppression définitive se font dans Gmail.

## Téléphone

La V1 reste disponible dans l’onglet Téléphone : espace interne libre/utilisé, analyse du dossier choisi, classement par taille, catégories, recherche, ouverture et suppression définitive avec confirmation. Analyse locale limitée à 50 000 fichiers / 10 000 dossiers. Les restrictions du sélecteur Android restent applicables.

## Télécharger et installer

1. **Actions → Android APK → dernière exécution verte de main**.
2. Télécharger l’artifact **SpaceSaver-APK** et extraire le ZIP.
3. Installer `SpaceSaver-0.2.0-debug.apk` sur Android. Le ZIP inclut l’empreinte publique du certificat dans `Google-OAuth-certificate.txt` pour Google Cloud.

Cette version utilise une clé debug générée par le runner. Elle peut différer de la V1 ou d’une prochaine compilation : Android peut demander de désinstaller l’ancienne version et le client OAuth doit correspondre au SHA-1 du nouvel APK. **Signature de test uniquement.** Une clé de distribution stable devra être configurée dans les secrets GitHub avant diffusion durable. Aucune clé privée de signature n’est publiée dans les artifacts, les logs ou le dépôt.

APK conservés 30 jours. Workflow lancé à chaque push sur main, sur PR ou manuellement. Compilation, tests unitaires et Android Lint précèdent la mise à disposition de l’APK.

## Développement cloud

`.devcontainer` installe Java 17, Gradle 8.11.1 et SDK Android 35. Dans le Codespace existant, récupérer main et lancer **Codespaces: Rebuild Container**. La préparation accepte les licences Android et génère le wrapper Gradle.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Données et accès

Connexion directe à Google par HTTPS. Jeton et aperçu Gmail restent en mémoire ; aucun token, objet, expéditeur ou corps de mail n’est écrit dans les logs ou envoyé à un serveur SpaceSaver. Corps et pièces jointes non téléchargés. Fermer la session locale efface l’aperçu et le jeton de l’app ; la révocation Google s’effectue dans [les connexions du compte](https://myaccount.google.com/connections). Google Play services peut conserver les autorisations Google.

Le scope `gmail.modify` permet de lire les métadonnées et de mettre les messages à la corbeille. Il accorde des capacités plus larges que celles utilisées par l’app ; SpaceSaver n’implémente ni envoi d’e-mails, ni suppression définitive Gmail. Ce scope restreint est soumis aux exigences Google avant une diffusion publique.

## Vérification

Les tests couvrent les seuils d’âge, les années bissextiles, le OU entre règles, les exclusions, les IDs sélectionnés, un changement de compte, un message devenu favori, les interruptions et les résultats partiels. Android Lint et la compilation ne remplacent pas une vérification réelle avec un compte de test.

Sur téléphone : annuler l’autorisation puis connecter un compte de test ; vérifier âges et combinaisons ; changer une règle et vérifier que l’ancien aperçu disparaît ; charger plusieurs pages ; annuler une confirmation ; déplacer quelques messages sacrifiables seulement ; vérifier leur présence dans la corbeille et que les autres restent en place ; restaurer depuis Gmail ; tester une coupure réseau et la rotation de l’écran. **Aucun test automatisé ne touche une boîte Gmail réelle.**

Google Drive, Google Photos, OneDrive et iCloud ne sont pas intégrés dans cette V2.
