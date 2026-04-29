{
  description = "ha-media-bridge — Android dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs   = nixpkgs.legacyPackages.${system};

      android = pkgs.androidenv.composeAndroidPackages {
        buildToolsVersions = [ "34.0.0" ];
        platformVersions   = [ "34" ];
        includeNDK         = false;
      };
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [ pkgs.jdk17 pkgs.android-tools android.androidsdk ];

        ANDROID_HOME = "${android.androidsdk}/libexec/android-sdk";
        JAVA_HOME    = "${pkgs.jdk17}";

        shellHook = ''
          echo "ha-media-bridge dev shell"
          echo "  Build:   ./gradlew assembleDebug"
          echo "  Install: adb install app/build/outputs/apk/debug/app-debug.apk"
        '';
      };
    };
}
