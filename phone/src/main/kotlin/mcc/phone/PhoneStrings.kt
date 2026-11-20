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
    abstract val background: String; abstract val backgroundHelp: String; abstract val battery: String; abstract val batteryDone: String
    abstract val huaweiHelp: String; abstract val safetyNote: String; abstract val about: String
    abstract val evFinished: String; abstract fun evFinishedText(r: String?): String; abstract val evNeedsYou: String
    abstract val evWeighed: String; abstract fun evWeighedText(g: Int?): String; abstract val evRunDone: String; abstract val evRunDoneText: String
    abstract val evStopped: String; abstract val evError: String
    abstract val channelLive: String; abstract val channelAlerts: String; abstract val channelLink: String
    abstract val retry: String; abstract val noRepo: String; abstract val steps: String; abstract fun servings(s: String): String
    abstract val machineIsOn: String; abstract val stopSent: String; abstract val pauseSent: String; abstract val tareSent: String
    abstract val testConnection: String; abstract val connectedOk: String; abstract val stepNow: String
    abstract val machineHere: String; abstract val noRecipeLoaded: String; abstract val saved: String
    abstract val language: String; abstract val systemDefault: String
    abstract val greetMorning: String; abstract val greetAfternoon: String; abstract val greetEvening: String
    abstract val cookMode: String; abstract val cookModeHelp: String; abstract fun bought(n: Int, total: Int): String; abstract val featured: String
    abstract val look: String; abstract val wallpaperColors: String; abstract val wallpaperColorsHelp: String
    abstract val stopConfirm: String; abstract val stopConfirmText: String; abstract val cancel: String
    abstract val nothingCooking: String; abstract val whatsCooking: String; abstract val temperature: String; abstract val scale: String
    abstract val next: String; abstract val previous: String; abstract fun removed(t: String): String; abstract val undo: String
    abstract val goToMachineStep: String
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
    override val battery = "Allow running in the background"; override val batteryDone = "Background use allowed"
    override val huaweiHelp = "Huawei phones: Settings → Battery → App launch → Monsieur Cuisine → Manage manually, and switch everything on."
    override val safetyNote = "The phone can watch, pause and stop. It only starts a run that was prepared and allowed at the machine, and opening a recipe never starts it."
    override val about = "About"
    override val evFinished = "Ready!"; override fun evFinishedText(r: String?) = if (r != null) "$r is ready." else "The recipe is ready."
    override val evNeedsYou = "The recipe needs you"
    override val evWeighed = "Weight reached"; override fun evWeighedText(g: Int?) = if (g != null) "$g g on the scale." else "Weight reached."
    override val evRunDone = "Done"; override val evRunDoneText = "The manual run has finished."
    override val evStopped = "Stopped"; override val evError = "Machine error"
    override val channelLive = "Cooking progress"; override val channelAlerts = "Machine alerts"; override val channelLink = "Connection"
    override val retry = "Try again"; override val noRepo = "Set the recipe repository address in Settings."
    override val steps = "Steps"; override fun servings(s: String) = if (s.all { it.isDigit() }) "Serves $s" else s
    override val machineIsOn = "Cooking on the machine"; override val stopSent = "Stop sent"; override val pauseSent = "Pause sent"; override val tareSent = "Scale zeroed"
    override val testConnection = "Connection"; override val connectedOk = "Connected"; override val stepNow = "Now"
    override val machineHere = "On the machine"; override val noRecipeLoaded = "No recipe open on the machine"; override val saved = "Saved"
    override val language = "Language"; override val systemDefault = "Phone language"
    override val greetMorning = "Good morning"; override val greetAfternoon = "Good afternoon"; override val greetEvening = "Good evening"
    override val cookMode = "Cook along"; override val cookModeHelp = "Big steps, screen stays on"
    override fun bought(n: Int, total: Int) = "$n of $total bought"; override val featured = "Try today"
    override val look = "Look"; override val wallpaperColors = "Wallpaper colours"
    override val wallpaperColorsHelp = "Use the colours of your wallpaper instead of the machine's orange (Android 12 and newer)."
    override val stopConfirm = "Stop the machine?"; override val stopConfirmText = "The motor and heating stop straight away."; override val cancel = "Cancel"
    override val nothingCooking = "Nothing cooking"; override val whatsCooking = "What's cooking"; override val temperature = "Temperature"; override val scale = "Scale"
    override val next = "Next"; override val previous = "Back"; override fun removed(t: String) = "Removed $t"; override val undo = "Undo"
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
    override val battery = "Permitir execução em segundo plano"; override val batteryDone = "Execução em segundo plano permitida"
    override val huaweiHelp = "Telemóveis Huawei: Definições → Bateria → Início de apps → Monsieur Cuisine → Gerir manualmente, e ligue tudo."
    override val safetyNote = "O telemóvel pode ver, pausar e parar. Só inicia o que foi preparado e permitido na máquina, e abrir uma receita nunca a inicia."
    override val about = "Sobre"
    override val evFinished = "Pronta!"; override fun evFinishedText(r: String?) = if (r != null) "$r está pronta." else "A receita está pronta."
    override val evNeedsYou = "A receita precisa de si"
    override val evWeighed = "Peso atingido"; override fun evWeighedText(g: Int?) = if (g != null) "$g g na balança." else "Peso atingido."
    override val evRunDone = "Terminado"; override val evRunDoneText = "O modo manual terminou."
    override val evStopped = "Parou"; override val evError = "Erro da máquina"
    override val channelLive = "Progresso a cozinhar"; override val channelAlerts = "Avisos da máquina"; override val channelLink = "Ligação"
    override val retry = "Tentar de novo"; override val noRepo = "Defina o endereço do repositório de receitas em Definições."
    override val steps = "Passos"; override fun servings(s: String) = if (s.all { it.isDigit() }) "$s pessoas" else s
    override val machineIsOn = "A cozinhar na máquina"; override val stopSent = "Parar enviado"; override val pauseSent = "Pausa enviada"; override val tareSent = "Balança a zero"
    override val testConnection = "Ligação"; override val connectedOk = "Ligado"; override val stepNow = "Agora"
    override val machineHere = "Na máquina"; override val noRecipeLoaded = "Nenhuma receita aberta na máquina"; override val saved = "Guardado"
    override val language = "Idioma"; override val systemDefault = "Idioma do telemóvel"
    override val greetMorning = "Bom dia"; override val greetAfternoon = "Boa tarde"; override val greetEvening = "Boa noite"
    override val cookMode = "Cozinhar passo a passo"; override val cookModeHelp = "Passos em grande, ecrã sempre ligado"
    override fun bought(n: Int, total: Int) = "$n de $total comprados"; override val featured = "Para hoje"
    override val look = "Aspeto"; override val wallpaperColors = "Cores do fundo de ecrã"
    override val wallpaperColorsHelp = "Usar as cores do seu fundo de ecrã em vez do laranja da máquina (Android 12 ou mais recente)."
    override val stopConfirm = "Parar a máquina?"; override val stopConfirmText = "O motor e o aquecimento param de imediato."; override val cancel = "Cancelar"
    override val nothingCooking = "Nada ao lume"; override val whatsCooking = "A cozinhar"; override val temperature = "Temperatura"; override val scale = "Balança"
    override val next = "Seguinte"; override val previous = "Anterior"; override fun removed(t: String) = "Removido: $t"; override val undo = "Anular"
    override val goToMachineStep = "Ir para o passo da máquina"
}

object P {
    @Volatile var s: PhoneStrings = PhonePt
    fun set(code: String) {
        s = if (code == "en") PhoneEn else PhonePt
        mcc.app.i18n.L.set(code)
    }
}
