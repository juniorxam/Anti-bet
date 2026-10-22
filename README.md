# AntiBet Android

Aplicativo Android experimental para ajudar a bloquear alguns domínios conhecidos de apostas e cassinos usando uma VPN local que filtra consultas DNS.

## O que esta versão faz
- Solicita autorização do Android para criar uma VPN local.
- Encaminha consultas DNS IPv4/UDP permitidas para o resolvedor público Cloudflare (1.1.1.1).
- Responde NXDOMAIN para domínios presentes na lista de bloqueio embutida no código.
- Não exige conta e não envia histórico de navegação a um servidor próprio.

## Limitações importantes
Este projeto ainda é um protótipo e precisa de testes em aparelhos reais. A lista de domínios é limitada e pode não incluir todas as casas de apostas. DNS sobre HTTPS (DoH), DNS sobre TLS (DoT), IPv6, DNS por TCP, domínios novos e anúncios incorporados diretamente em vídeos ou aplicativos podem escapar do filtro. Não trate o aplicativo como proteção infalível nem como substituto de ferramentas de autocontrole ou apoio especializado.

## Gerar o APK
O workflow **Build Android APK** tenta compilar o APK de depuração quando há um push para a branch `main`.
1. Abra a aba **Actions** deste repositório.
2. Selecione **Build Android APK** e aguarde a execução terminar.
3. Se a execução ficar verde, abra-a e baixe o artefato `antibet-debug-apk`.
4. Extraia o ZIP e instale `app-debug.apk` no Android. Talvez seja necessário autorizar a instalação por essa origem.

O APK só deve ser considerado disponível quando uma execução terminar com sucesso e o artefato aparecer. Para compilar localmente, abra o projeto no Android Studio com JDK 17 e execute `gradle assembleDebug`.

## Privacidade e permissões
O Android exibirá uma solicitação de autorização para a VPN local. O aplicativo precisa de acesso à rede para encaminhar consultas DNS ao resolvedor configurado. Nenhuma conexão VPN de terceiros é usada.

## Compatibilidade
Android 8.0 (API 26) ou superior. A compatibilidade real depende de testes no aparelho e da configuração de DNS do sistema.