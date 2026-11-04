# SpaceSaver

Application Android native **Kotlin + Jetpack Compose**, interface française. Première version fonctionnelle 0.1.0, Android 8.0 minimum.

## Fonctions

- Espace libre/utilisé du stockage interne.
- Choix explicite d’un dossier via le sélecteur Android, analyse récursive en arrière-plan et annulation.
- Fichiers classés du plus volumineux au plus petit ; filtres Photos, Vidéos, Audio, Documents, Autres et recherche.
- Ouverture des fichiers avec les applications du téléphone.
- Sélection manuelle et suppression définitive avec confirmation, compte rendu des refus et gestion lecture seule.
- Aucun compte, aucune publicité, aucune permission Internet. SpaceSaver ne transfère pas vos fichiers.

## Télécharger l’APK

1. Ouvrir **Actions → Android APK** dans ce dépôt.
2. Ouvrir la dernière exécution verte de la branche `main`.
3. Dans **Artifacts**, télécharger **SpaceSaver-APK** (connexion GitHub nécessaire).
4. Extraire le ZIP, transférer `SpaceSaver-0.1.0-debug.apk` sur Android et ouvrir le fichier.
5. Autoriser, si Android le demande, l’installation depuis cette source.

Le workflow compile l’application, lance les tests unitaires et Android Lint, puis conserve l’APK 30 jours. Il se lance à chaque push sur main ou manuellement via Run workflow. L’APK est signé automatiquement pour les tests ; ce n’est pas une version Play Store. La clé debug peut changer entre compilations : si Android refuse une mise à jour, désinstaller l’ancienne version avant d’installer la nouvelle. Les fichiers personnels ne sont pas effacés par la désinstallation de SpaceSaver.

## Développer entièrement dans le cloud

Le dossier `.devcontainer` configure Java 17, Gradle 8.11.1 et le SDK Android 35. Dans le Codespace existant, récupérer main, puis **Codespaces: Rebuild Container**. La configuration accepte les licences du SDK Android pour installer les outils de compilation. Rien n’est installé sur le PC.

Le script de préparation génère le wrapper Gradle dans le Codespace. Ensuite :

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK local au Codespace : `app/build/outputs/apk/debug/app-debug.apk`.

## Périmètre et limites de cette version

Le sélecteur Android donne accès uniquement au dossier choisi. Sur Android récent, la racine du stockage, la racine Download et Android/data peuvent être bloquées : choisir un sous-dossier, DCIM ou Pictures. L’espace interne affiché et le volume du dossier analysé sont deux mesures distinctes, notamment pour une carte SD ou un fournisseur cloud.

L’analyse est limitée à 50 000 fichiers / 10 000 dossiers pour protéger la mémoire ; un message signale un résultat partiel ou des dossiers inaccessibles. Une taille inconnue est affichée à 0 o. Un fournisseur installé sur le téléphone peut proposer un dossier distant et utiliser sa propre connexion réseau. Supprimer un fichier synchronisé peut aussi le supprimer du cloud. Aucune suppression automatique, aucune corbeille SpaceSaver.

Pas encore de détection de doublons par contenu, de connecteur Gmail/OneDrive/iCloud, de nettoyage des caches d’autres applications ni de publication Play Store.

## Vérification sur téléphone

Utiliser d’abord un dossier de test contenant une photo, une vidéo, un PDF et deux fichiers sacrifiables. Vérifier le classement par taille, les filtres et la recherche. Ouvrir un fichier ; sélectionner un fichier, annuler la confirmation et vérifier sa présence ; confirmer ensuite sa suppression et vérifier qu’un fichier non sélectionné reste présent. Tourner l’écran pendant une analyse ; annuler et relancer. Vérifier le traitement d’un dossier vide et d’un accès révoqué. La compilation CI ne remplace pas ce test sur appareil.
