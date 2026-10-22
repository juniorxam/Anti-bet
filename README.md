# AntiBet Android

Aplicativo Android experimental para ajudar a bloquear domínios conhecidos de apostas, cassinos e publicidade relacionada por meio de uma VPN local de DNS.

## Estado
Projeto inicial em desenvolvimento. O bloqueio depende das consultas DNS que passam pelo serviço; não bloqueia anúncios embutidos no conteúdo de aplicativos e não é uma proteção infalível. Não substitui ferramentas de autocontrole ou apoio especializado.

## Compilar APK
O GitHub Actions compila o APK de depuração automaticamente quando o projeto é enviado para a branch `main`. Acesse **Actions → Build Android APK** e baixe o artefato `antibet-debug-apk` após uma execução concluída com sucesso.

Também é possível abrir o projeto no Android Studio e executar a tarefa `assembleDebug`.

## Permissões e privacidade
O Android solicita autorização explícita para ativar a VPN local. O aplicativo não precisa de conta, não envia histórico de navegação a um servidor próprio e usa uma lista local de domínios. O serviço ainda é um protótipo: revise e teste antes de depender dele para bloquear conteúdo.

## Compatibilidade
Android 8.0 (API 26) ou superior.