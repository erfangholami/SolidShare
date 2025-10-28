# Solid Share

Solid Share project is an open-source Android application that allows citizens to use their [Solid](https://solidproject.org/) pods as a data wallet. It allows user to login into their Solid pods with different accounts, managing their data, share private files utilizing a QR code or a generated link, ability to sync other Solid data modules (such as Contacts) with Android ecosystem, and having the opportunity to have travel tickets and passes inside their pods and use them on a daily-basis. The app is designed to be offline-first for more convenience.
The goal of this project is to bring Solid into the hands of regular people, without having a background in programming, making the public aware of the existence of the Solid project, and giving them the opportunity to have a smooth and easy experience on their Android phones.

It uses [Android Solid Services](https://github.com/pondersource/Android-Solid-Services) for communicating with pods.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```



## Acknowledgments
This project funded by [NLnet](https://nlnet.nl/) as a part of [Mobifree](https://mobifree.org/) <img src="https://nlnet.nl/logo/banner.svg" style="width: 10%; margin: 0 1% 0 1%;">
/ <img src="https://nlnet.nl/image/logos/NGI_Mobifree_tag.svg" style="width: 10%; margin: 0 1% 0 1%;">
