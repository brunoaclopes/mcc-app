package mcc.phone

/**
 * Phone-only texts. Everything the machine also says (step sentences, board errors, stop reasons, units) comes from
 * the shared [mcc.app.i18n.Strings] in :common, so both apps word things the same way.
 */
abstract class PhoneStrings {
    abstract val tabMachine: String; abstract val tabRecipes: String; abstract val tabList: String; abstract val tabSettings: String
    abstract val connecting: String; abstract val notConfigured: String; abstract val machineOffline: String
    abstract fun brokerError(m: String): String
    abstract val idle: String; abstract val runningWord: String; abstract val pausedWord: String; abstract val finished: String
    abstract val errorWord: String; abstract val disconnected: String
    abstract val manualRun: String; abstract fun readyAt(t: String): String; abstract val idleDetail: String
    abstract val remoteReady: String; abstract fun remoteUntil(t: String): String; abstract val holdToStart: String
    abstract val openOnMachine: String; abstract val openedOnMachine: String; abstract val notOpened: String
    abstract val cookingNow: String; abstract val addToList: String; abstract fun addedToList(n: Int): String
    abstract val listTitle: String; abstract val listEmpty: String; abstract val clearChecked: String; abstract val share: String
    abstract val connection: String; abstract val tls: String; abstract val saveConnect: String; abstract val repoHint: String
    abstract val notifications: String; abstract val notifNeedsYou: String; abstract val notifFinished: String
    abstract val notifWeighed: String; abstract val notifProblems: String; abstract val notifLive: String; abstract val notifLiveHelp: String
    abstract val idleNotifHelp: String; abstract val idleNotifButton: String
    abstract val background: String; abstract val backgroundHelp: String; abstract val battery: String; abstract val batteryDone: String
    abstract val huaweiHelp: String; abstract val safetyNote: String; abstract val about: String
    abstract val evFinished: String; abstract fun evFinishedText(r: String?): String; abstract val evNeedsYou: String
    abstract val evWeighed: String; abstract fun evWeighedText(g: Int?): String; abstract val evRunDone: String; abstract val pauseExpired: String; abstract val evRunDoneText: String
    abstract val evStopped: String; abstract val evError: String
    abstract val channelLive: String; abstract val channelAlerts: String; abstract val channelLink: String
    abstract val retry: String; abstract val noRepo: String; abstract val steps: String; abstract fun servings(s: String): String; abstract val servingsOnMachine: String
    abstract val machineIsOn: String; abstract val stopSent: String; abstract val pauseSent: String; abstract val tareSent: String
    abstract val testConnection: String; abstract val connectedOk: String; abstract val stepNow: String
    abstract val machineHere: String; abstract val noRecipeLoaded: String; abstract val saved: String
    abstract val language: String; abstract val systemDefault: String
    abstract val greetMorning: String; abstract val greetAfternoon: String; abstract val greetEvening: String
    abstract val cookMode: String; abstract val cookModeHelp: String; abstract fun bought(n: Int, total: Int): String; abstract val featured: String
    abstract val look: String; abstract val wallpaperColors: String; abstract val wallpaperColorsHelp: String
    abstract val keepRecipe: String; abstract val cancelRecipe: String; abstract val cancelRecipeTitle: String; abstract val cancelRecipeText: String
    abstract val stopConfirm: String; abstract val stopConfirmText: String; abstract val cancel: String
    abstract val nothingCooking: String; abstract val whatsCooking: String; abstract val temperature: String; abstract val scale: String
    abstract val next: String; abstract val previous: String; abstract fun removed(t: String): String; abstract val undo: String
    abstract fun clearedN(n: Int): String; abstract fun discarded(t: String): String
    abstract val goToMachineStep: String
    abstract val importTitle: String; abstract val importHelp: String; abstract val importGo: String; abstract fun importStage(n: Int, sec: Int): String
    abstract fun importReady(t: String): String; abstract fun importSummary(ingredients: Int, steps: Int, machine: Int): String
    abstract val publish: String; abstract val keepOnPhone: String; abstract val discard: String; abstract val publishHelp: String; abstract val publishOff: String
    abstract fun publishFailed(m: String): String; abstract fun published(t: String): String; abstract fun publishedPending(t: String): String
    abstract fun savedOnPhone(t: String): String; abstract val noSecondLanguage: String; abstract val importNotSet: String; abstract val openSettings: String
    abstract val importSettings: String; abstract val importSettingsHelp: String; abstract val publishAddress: String; abstract val publishToken: String
    abstract val testAi: String
    abstract val feedbackLabel: String; abstract val feedbackHint: String; abstract val feedbackSend: String; abstract fun revised(n: Int): String
    abstract val videoWait: String; abstract val videoDescriptionOnly: String; abstract val videoPartial: String; abstract val uploadTokenWrong: String
    abstract val adjustedTitle: String
}

object PhoneEn : PhoneStrings() {
    override val tabMachine = "Machine"; override val tabRecipes = "Recipes"; override val tabList = "Shopping"; override val tabSettings = "Settings"
    override val connecting = "Connecting…"; override val notConfigured = "Set up the connection in Settings"
    override val machineOffline = "The machine is not connected to the broker"
    override fun brokerError(m: String) = "Cannot reach the broker: $m"
    override val idle = "Ready"; override val runningWord = "Cooking"; override val pausedWord = "Paused"; override val finished = "Finished"
    override val errorWord = "Error"; override val disconnected = "Offline"
    override val manualRun = "Manual mode"; override fun readyAt(t: String) = "Ready at $t"; override val idleDetail = "Waiting for you at the machine"
    override val remoteReady = "Ready to start remotely"; override fun remoteUntil(t: String) = "Allowed until $t"; override val holdToStart = "Hold to start"
    override val openOnMachine = "Open on the machine"; override val openedOnMachine = "Open on the machine. Press Start there when you are ready."
    override val notOpened = "The machine did not open it. Another recipe may be under way, or \"Recipes from the phone\" is off on the machine."
    override val cookingNow = "Cooking now"; override val addToList = "Add to shopping list"
    override fun addedToList(n: Int) = if (n == 1) "1 ingredient added" else "$n ingredients added"
    override val listTitle = "Shopping list"; override val listEmpty = "Nothing here yet. Add ingredients from a recipe."
    override val clearChecked = "Clear ticked"; override val share = "Share"
    override val connection = "Connection (MQTT)"; override val tls = "Secure connection (TLS)"; override val saveConnect = "Save and connect"
    override val repoHint = "The same recipe repository address the machine uses"
    override val notifications = "Notifications"; override val notifNeedsYou = "When the recipe needs you"; override val notifFinished = "When it finishes"
    override val notifWeighed = "When the weight is reached"; override val notifProblems = "Stops and errors"
    override val notifLive = "Live progress while cooking"; override val notifLiveHelp = "Time left and the step on the lock screen, with Pause and Stop."
    override val background = "Stay connected in the background"
    override val backgroundHelp = "Needed for notifications with the app closed. Shows a quiet notification while connected."
    override val idleNotifHelp = "Android needs a notification to stay connected. Swipe the \"Ready\" one away and it stays hidden until something cooks, or turn its category off: cooking progress and alerts are separate and keep working."
    override val idleNotifButton = "Hide the idle notification"
    override val battery = "Allow running in the background"; override val batteryDone = "Background use allowed"
    override val huaweiHelp = "Huawei phones: Settings → Battery → App launch → Monsieur Cuisine → Manage manually, and switch everything on."
    override val safetyNote = "The phone can watch, pause and stop. It only starts a run that was prepared and allowed at the machine, and opening a recipe never starts it."
    override val about = "About"
    override val evFinished = "Ready!"; override fun evFinishedText(r: String?) = if (r != null) "$r is ready." else "The recipe is ready."
    override val evNeedsYou = "The recipe needs you"
    override val evWeighed = "Weight reached"; override fun evWeighedText(g: Int?) = if (g != null) "$g g on the scale." else "Weight reached."
    override val evRunDone = "Done"; override val pauseExpired = "It stayed paused for over an hour, so the run was cancelled."; override val evRunDoneText = "The manual run has finished."
    override val evStopped = "Stopped"; override val evError = "Machine error"
    override val channelLive = "Cooking progress"; override val channelAlerts = "Machine alerts"; override val channelLink = "Connection"
    override val retry = "Try again"; override val noRepo = "Set the recipe repository address in Settings."
    override val steps = "Steps"; override fun servings(s: String) = if (s.all { it.isDigit() }) "Serves $s" else s
    override val servingsOnMachine = "Set when it was opened on the machine."
    override val machineIsOn = "Cooking on the machine"; override val stopSent = "Stop sent"; override val pauseSent = "Pause sent"; override val tareSent = "Scale zeroed"
    override val testConnection = "Connection"; override val connectedOk = "Connected"; override val stepNow = "Now"
    override val machineHere = "On the machine"; override val noRecipeLoaded = "No recipe open on the machine"; override val saved = "Saved"
    override val language = "Language"; override val systemDefault = "Phone language"
    override val importTitle = "Import a recipe"
    override val importHelp = "Paste the link of a recipe page, or the recipe text. Settings from other machines (\"10 min/100°C/speed 1\") are converted. You can also share a page from the browser to Monsieur Cuisine."
    override val importGo = "Import"
    override fun importStage(n: Int, sec: Int) = listOf("Reading the page and writing the recipe…", "Translating…", "Sending to the recipe server…", "Waiting for the server to list it…", "Applying your changes…", "Reading the video…")[n] + " $sec s"
    override fun importReady(t: String) = "Recipe ready: $t"
    override fun importSummary(ingredients: Int, steps: Int, machine: Int) = "$ingredients ingredients · $steps steps, $machine on the machine"
    override val publish = "Add to the recipe server"; override val keepOnPhone = "Keep on this phone only"; override val discard = "Discard"
    override val publishHelp = "Goes to the recipe repository in Portuguese and English, so the machine and every phone get it."
    override val publishOff = "Set the upload service in Settings → Recipe import to add recipes to the server."
    override fun publishFailed(m: String) = "Could not add it to the server: $m"
    override fun published(t: String) = "Added to the recipe server: $t"
    override fun publishedPending(t: String) = "Sent: $t. The server hasn't listed it yet; it shows up after the next sync."
    override fun savedOnPhone(t: String) = "Saved on this phone: $t"
    override val noSecondLanguage = "The translation failed, so it can't go to the server (it needs both languages). Keep it on this phone or try again."
    override val importNotSet = "Set the AI server first: Settings → Recipe import."
    override val openSettings = "Open Settings"
    override val importSettings = "Recipe import"
    override val importSettingsHelp = "The AI turns recipe pages into machine recipes (it only writes recipes: it can't run the machine). The upload service adds them to the recipe server."
    override val publishAddress = "Upload service address"; override val publishToken = "Upload token"
    override val testAi = "Save and test"
    override val feedbackLabel = "Something to change?"
    override val feedbackHint = "e.g. there's no garlic in the video · brown it 2 min longer · it's for 2 people"
    override val feedbackSend = "Ask for changes"
    override fun revised(n: Int) = if (n == 1) "Changed once with your feedback" else "Changed $n times with your feedback"
    override val videoWait = "Videos with subtitles take seconds. Without them, the server listens to the audio: a few minutes for a long video."
    override val videoDescriptionOnly = "Made from the title and description only: the video has no subtitles or speech the server could use. Check it carefully."
    override val videoPartial = "The transcript only covers the start of the video, so the end of the recipe may be missing."
    override val uploadTokenWrong = "The upload token is wrong: check Settings → Recipe import."
    override val adjustedTitle = "Adjusted to the machine"
    override val greetMorning = "Good morning"; override val greetAfternoon = "Good afternoon"; override val greetEvening = "Good evening"
    override val cookMode = "Cook along"; override val cookModeHelp = "Big steps, screen stays on"
    override fun bought(n: Int, total: Int) = "$n of $total bought"; override val featured = "Try today"
    override val look = "Look"; override val wallpaperColors = "Wallpaper colours"
    override val wallpaperColorsHelp = "Use the colours of your wallpaper instead of the machine's orange (Android 12 and newer)."
    override val keepRecipe = "Keep recipe"; override val cancelRecipe = "Cancel recipe"; override val cancelRecipeTitle = "Cancel the recipe?"
    override val cancelRecipeText = "It closes on the machine, and the machine stops if it is running."
    override val stopConfirm = "Stop the machine?"; override val stopConfirmText = "The motor and heating stop straight away."; override val cancel = "Cancel"
    override val nothingCooking = "Nothing cooking"; override val whatsCooking = "What's cooking"; override val temperature = "Temperature"; override val scale = "Scale"
    override val next = "Next"; override val previous = "Back"; override fun removed(t: String) = "Removed $t"; override val undo = "Undo"
    override fun clearedN(n: Int) = if (n == 1) "Removed 1 bought item" else "Removed $n bought items"
    override fun discarded(t: String) = "Discarded: $t"
    override val goToMachineStep = "Go to the machine's step"
}

object PhonePt : PhoneStrings() {
    override val tabMachine = "Máquina"; override val tabRecipes = "Receitas"; override val tabList = "Compras"; override val tabSettings = "Definições"
    override val connecting = "A ligar…"; override val notConfigured = "Configure a ligação em Definições"
    override val machineOffline = "A máquina não está ligada ao servidor"
    override fun brokerError(m: String) = "Sem ligação ao servidor: $m"
    override val idle = "Pronta"; override val runningWord = "A cozinhar"; override val pausedWord = "Em pausa"; override val finished = "Terminado"
    override val errorWord = "Erro"; override val disconnected = "Desligada"
    override val manualRun = "Modo manual"; override fun readyAt(t: String) = "Pronta às $t"; override val idleDetail = "À sua espera na máquina"
    override val remoteReady = "Pronta a iniciar à distância"; override fun remoteUntil(t: String) = "Permitido até às $t"; override val holdToStart = "Mantenha para iniciar"
    override val openOnMachine = "Abrir na máquina"; override val openedOnMachine = "Aberta na máquina. Carregue em Iniciar lá quando estiver pronto."
    override val notOpened = "A máquina não a abriu. Pode haver outra receita a meio, ou \"Receitas do telemóvel\" está desligado na máquina."
    override val cookingNow = "A cozinhar agora"; override val addToList = "Adicionar à lista de compras"
    override fun addedToList(n: Int) = if (n == 1) "1 ingrediente adicionado" else "$n ingredientes adicionados"
    override val listTitle = "Lista de compras"; override val listEmpty = "Ainda está vazia. Adicione ingredientes a partir de uma receita."
    override val clearChecked = "Limpar marcados"; override val share = "Partilhar"
    override val connection = "Ligação (MQTT)"; override val tls = "Ligação segura (TLS)"; override val saveConnect = "Guardar e ligar"
    override val repoHint = "O mesmo endereço do repositório de receitas que a máquina usa"
    override val notifications = "Notificações"; override val notifNeedsYou = "Quando a receita precisa de si"; override val notifFinished = "Quando termina"
    override val notifWeighed = "Quando o peso é atingido"; override val notifProblems = "Paragens e erros"
    override val notifLive = "Progresso ao vivo a cozinhar"; override val notifLiveHelp = "Tempo restante e passo no ecrã de bloqueio, com Pausa e Parar."
    override val background = "Manter ligado em segundo plano"
    override val backgroundHelp = "Necessário para receber notificações com a app fechada. Mostra uma notificação discreta enquanto está ligado."
    override val idleNotifHelp = "O Android precisa de uma notificação para manter a ligação. Deslize a de \"Pronta\" para a esconder até a máquina começar a cozinhar, ou desligue essa categoria: o progresso e os alertas são à parte e continuam a funcionar."
    override val idleNotifButton = "Esconder a notificação em espera"
    override val battery = "Permitir execução em segundo plano"; override val batteryDone = "Execução em segundo plano permitida"
    override val huaweiHelp = "Telemóveis Huawei: Definições → Bateria → Início de apps → Monsieur Cuisine → Gerir manualmente, e ligue tudo."
    override val safetyNote = "O telemóvel pode ver, pausar e parar. Só inicia o que foi preparado e permitido na máquina, e abrir uma receita nunca a inicia."
    override val about = "Sobre"
    override val evFinished = "Pronta!"; override fun evFinishedText(r: String?) = if (r != null) "$r está pronta." else "A receita está pronta."
    override val evNeedsYou = "A receita precisa de si"
    override val evWeighed = "Peso atingido"; override fun evWeighedText(g: Int?) = if (g != null) "$g g na balança." else "Peso atingido."
    override val evRunDone = "Terminado"; override val pauseExpired = "Ficou em pausa mais de uma hora e foi cancelado."; override val evRunDoneText = "O modo manual terminou."
    override val evStopped = "Parou"; override val evError = "Erro da máquina"
    override val channelLive = "Progresso a cozinhar"; override val channelAlerts = "Avisos da máquina"; override val channelLink = "Ligação"
    override val retry = "Tentar de novo"; override val noRepo = "Defina o endereço do repositório de receitas em Definições."
    override val steps = "Passos"; override fun servings(s: String) = if (s.all { it.isDigit() }) "$s pessoas" else s
    override val servingsOnMachine = "Definido quando foi aberta na máquina."
    override val machineIsOn = "A cozinhar na máquina"; override val stopSent = "Parar enviado"; override val pauseSent = "Pausa enviada"; override val tareSent = "Balança a zero"
    override val testConnection = "Ligação"; override val connectedOk = "Ligado"; override val stepNow = "Agora"
    override val machineHere = "Na máquina"; override val noRecipeLoaded = "Nenhuma receita aberta na máquina"; override val saved = "Guardado"
    override val language = "Idioma"; override val systemDefault = "Idioma do telemóvel"
    override val importTitle = "Importar uma receita"
    override val importHelp = "Cole o endereço de uma página de receita, ou o texto da receita. As definições de outras máquinas (\"10 min/100°C/vel 1\") são convertidas. Também pode partilhar uma página do navegador para o Monsieur Cuisine."
    override val importGo = "Importar"
    override fun importStage(n: Int, sec: Int) = listOf("A ler a página e a escrever a receita…", "A traduzir…", "A enviar para o servidor de receitas…", "À espera que o servidor a mostre…", "A aplicar as suas alterações…", "A ler o vídeo…")[n] + " $sec s"
    override fun importReady(t: String) = "Receita pronta: $t"
    override fun importSummary(ingredients: Int, steps: Int, machine: Int) = "$ingredients ingredientes · $steps passos, $machine na máquina"
    override val publish = "Adicionar ao servidor de receitas"; override val keepOnPhone = "Guardar só neste telemóvel"; override val discard = "Descartar"
    override val publishHelp = "Vai para o repositório de receitas em português e inglês, para a máquina e todos os telemóveis a receberem."
    override val publishOff = "Configure o serviço de envio em Definições → Importar receitas para adicionar receitas ao servidor."
    override fun publishFailed(m: String) = "Não foi possível adicionar ao servidor: $m"
    override fun published(t: String) = "Adicionada ao servidor de receitas: $t"
    override fun publishedPending(t: String) = "Enviada: $t. O servidor ainda não a mostra; aparece na próxima sincronização."
    override fun savedOnPhone(t: String) = "Guardada neste telemóvel: $t"
    override val noSecondLanguage = "A tradução falhou, por isso não pode ir para o servidor (precisa das duas línguas). Guarde-a neste telemóvel ou tente de novo."
    override val importNotSet = "Configure primeiro o servidor de IA: Definições → Importar receitas."
    override val openSettings = "Abrir Definições"
    override val importSettings = "Importar receitas"
    override val importSettingsHelp = "A IA transforma páginas de receitas em receitas para a máquina (só escreve receitas: não pode ligar a máquina). O serviço de envio adiciona-as ao servidor de receitas."
    override val publishAddress = "Endereço do serviço de envio"; override val publishToken = "Token de envio"
    override val testAi = "Guardar e testar"
    override val feedbackLabel = "Alguma coisa a mudar?"
    override val feedbackHint = "p. ex. no vídeo não leva alho · alourar mais 2 min · é para 2 pessoas"
    override val feedbackSend = "Pedir alterações"
    override fun revised(n: Int) = if (n == 1) "Alterada uma vez com as suas indicações" else "Alterada $n vezes com as suas indicações"
    override val videoWait = "Vídeos com legendas demoram segundos. Sem legendas, o servidor ouve o áudio: alguns minutos num vídeo longo."
    override val videoDescriptionOnly = "Feita só com o título e a descrição: o vídeo não tem legendas nem fala que o servidor pudesse usar. Verifique-a com atenção."
    override val videoPartial = "A transcrição só cobre o início do vídeo, por isso pode faltar o fim da receita."
    override val uploadTokenWrong = "O token de envio está errado: verifique Definições → Importar receitas."
    override val adjustedTitle = "Ajustado à máquina"
    override val greetMorning = "Bom dia"; override val greetAfternoon = "Boa tarde"; override val greetEvening = "Boa noite"
    override val cookMode = "Cozinhar passo a passo"; override val cookModeHelp = "Passos em grande, ecrã sempre ligado"
    override fun bought(n: Int, total: Int) = "$n de $total comprados"; override val featured = "Para hoje"
    override val look = "Aspeto"; override val wallpaperColors = "Cores do fundo de ecrã"
    override val wallpaperColorsHelp = "Usar as cores do seu fundo de ecrã em vez do laranja da máquina (Android 12 ou mais recente)."
    override val keepRecipe = "Manter receita"; override val cancelRecipe = "Cancelar receita"; override val cancelRecipeTitle = "Cancelar a receita?"
    override val cancelRecipeText = "Fecha na máquina, e a máquina para se estiver a trabalhar."
    override val stopConfirm = "Parar a máquina?"; override val stopConfirmText = "O motor e o aquecimento param de imediato."; override val cancel = "Cancelar"
    override val nothingCooking = "Nada ao lume"; override val whatsCooking = "A cozinhar"; override val temperature = "Temperatura"; override val scale = "Balança"
    override val next = "Seguinte"; override val previous = "Anterior"; override fun removed(t: String) = "Removido: $t"; override val undo = "Anular"
    override fun clearedN(n: Int) = if (n == 1) "Removido 1 artigo comprado" else "Removidos $n artigos comprados"
    override fun discarded(t: String) = "Descartada: $t"
    override val goToMachineStep = "Ir para o passo da máquina"
}

object P {
    @Volatile var s: PhoneStrings = PhonePt
    fun set(code: String) {
        s = if (code == "en") PhoneEn else PhonePt
        mcc.app.i18n.L.set(code)
    }
}
