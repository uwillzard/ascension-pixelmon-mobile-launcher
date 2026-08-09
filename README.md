# Ascension Pixelmon Mobile Launcher — Android V1

Esta pasta é um projeto de customização/build do **Amethyst-Android** para o servidor
**Ascension Pixelmon**. Ela não redistribui o código-fonte completo do Amethyst:
o build baixa a base oficial e aplica os patches Ascension.

## Alvo

- Minecraft Java: 1.21.1
- Pixelmon: 9.3.16
- NeoForge: 21.1.200
- Java: 21
- Servidor: Jogar.AscensionPixelmon.com.br

## O que a V1 já implementa

- Nome Android: **Ascension Pixelmon Launcher**
- Package: `br.com.ascensionpixelmon.launcher`
- Tela inicial escura Ascension
- Botão **JOGAR**
- Botões para Site, Discord, Controles Touch e Arquivos do Jogo
- Atualizador Ascension executado **antes** de abrir o Minecraft
- Procura primeiro `mods-mobile.zip` na release `v1.0.0`
- Enquanto `mods-mobile.zip` não existir, usa o `mods.zip` atual como fallback
- Compara fingerprint/digest remoto com o instalado
- Baixa atualizações para pasta temporária
- Valida o ZIP e, quando disponível, o SHA-256 publicado pelo GitHub
- Mantém a pasta `mods` antiga até a nova estar pronta
- Faz rollback se a instalação nova falhar
- Remove backup e temporários após sucesso
- Instala `config.zip` e `options.txt` apenas na primeira instalação do perfil

## Limite desta V1

O launcher já cuida da identidade Ascension e do modpack, mas a **primeira criação/seleção
do perfil NeoForge 1.21.1** ainda usa o mecanismo normal do Amethyst. Depois que o perfil
NeoForge estiver criado e selecionado, o botão **JOGAR** passa pelo atualizador Ascension
automaticamente antes de iniciar o jogo.

## Gerar APK pelo GitHub Actions

1. Crie um repositório vazio no GitHub.
2. Extraia este ZIP e envie **todo o conteúdo desta pasta** para o repositório.
3. Entre em **Actions**.
4. Abra **Build Ascension Android APK**.
5. Clique em **Run workflow**.
6. Ao concluir, baixe o artifact **Ascension-Pixelmon-Mobile-APK**.

O APK gerado é uma build Debug instalável para testes.

## Gerar APK no Windows

Pré-requisitos:
- Git
- Java 21
- Android Studio / Android SDK instalado

Abra PowerShell nesta pasta e execute:

```powershell
.\BUILD-WINDOWS.ps1
```

Se o build terminar, o APK será copiado para:

`dist\Ascension-Pixelmon-Mobile-v1-debug.apk`

## Mods para celular

A V1 funciona sem mudar sua release porque cai para `mods.zip`.

Para a versão mobile ficar mais estável, publique depois na mesma release:

`mods-mobile.zip`

Esse pacote deve remover mods puramente gráficos/de desktop que não funcionem bem
no Android. O servidor continua sendo o mesmo.

## Arquivos principais

- `apply_ascension.py`: aplica a identidade Ascension e injeta o atualizador
- `branding/AscensionModpackUpdater.java`: atualização segura do modpack
- `branding/fragment_launcher.xml`: tela principal mobile
- `.github/workflows/build-apk.yml`: build automático do APK
- `BUILD-WINDOWS.ps1`: build local no Windows
- `ascension-mobile-config.json`: referências/versões do projeto

## Licença

O projeto de base Amethyst-Android declara GNU LGPLv3. Ao distribuir uma build modificada,
mantenha os avisos e obrigações de licença da base e das dependências.
