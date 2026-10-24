# Ringoame196-plugin-template
Ringoame196用Paperプラグインテンプレート

## [機能]
- **`./gradlew setup`**: 自動でブランチ切り替えやGitHub Topicsの追加
- **静的解析**: `detekt`、`kotlinter`による静的解析・フォーマットチェックの標準搭載
- **Gemini API連携**: `secrets.GEMINI_API_KEY` を設定することで、マージ時に一言アドバイスが可能
- **自動JARコピー**: `./server-config.properties` の設定により、build時にJARファイルをテストサーバーへ自動コピー

### 自動JARコピーの設定
`./server-config.properties` に以下を記述します。
```properties
deployPath=C:/path/to/your/minecraft/plugins
serverIp=127.0.0.1
serverPort=8080
```
※ [Development](https://github.com/ringoame196-s-mcPlugin/Development) と連携する必要があります。

## [クレジット]
当テンプレートおよびプラグインビルドでは、以下のオープンソースソフトウェア・ツールを利用しています：
- [plugin-yml](https://github.com/Minecrell/plugin-yml) (by Minecrell) - Gradle plugin for generating plugin configuration files.