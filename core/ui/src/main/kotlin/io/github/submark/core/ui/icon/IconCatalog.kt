package io.github.submark.core.ui.icon

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.submark.core.ui.R

enum class IconGroup(@StringRes val labelRes: Int) {
    ACTIVITIES(R.string.ui_icon_group_activities),
    APPS(R.string.ui_icon_group_apps),
    CATEGORIES(R.string.ui_icon_group_categories),
    DOCUMENTS(R.string.ui_icon_group_documents),
    MARKERS(R.string.ui_icon_group_markers),
    NATURE(R.string.ui_icon_group_nature),
    SHAPES(R.string.ui_icon_group_shapes),
    SYMBOLS(R.string.ui_icon_group_symbols),
    SYSTEM(R.string.ui_icon_group_system),
    MEDIA(R.string.ui_icon_group_media),
    FINANCE(R.string.ui_icon_group_finance),
}

/** One catalogue icon. [name] is the stable key persisted as `IconType.SYMBOL` value; never rename. */
class IconEntry internal constructor(
    val name: String,
    val group: IconGroup,
    val keywords: List<String>,
    factory: () -> ImageVector,
) {
    /** Built on first access; Material icon vectors are not free to construct. */
    val vector: ImageVector by lazy(factory)
}

/** Curated Material icon set used for SYMBOL icons (subscriptions, categories, tags, payment methods). */
object IconCatalog {

    val all: List<IconEntry> by lazy { buildEntries() }

    private val byName: Map<String, IconEntry> by lazy { all.associateBy { it.name } }

    operator fun get(name: String?): IconEntry? = name?.let { byName[it.trim().lowercase()] }

    fun vector(name: String?): ImageVector? = get(name)?.vector

    fun group(group: IconGroup): List<IconEntry> = all.filter { it.group == group }

    /** Case-insensitive match on name, keywords and group name. Blank query returns everything. */
    fun search(query: String): List<IconEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { e ->
            e.name.contains(q) || e.keywords.any { it.contains(q) } || e.group.name.lowercase().contains(q)
        }
    }

    private fun buildEntries(): List<IconEntry> {
        val list = mutableListOf<IconEntry>()
        fun add(group: IconGroup, name: String, keywords: String, factory: () -> ImageVector) {
            list += IconEntry(name, group, keywords.split(' ').filter { it.isNotBlank() }, factory)
        }
        IconGroup.ACTIVITIES.let { g ->
            add(g, "fitness", "gym workout sport 健身") { Icons.Rounded.FitnessCenter }
            add(g, "bike", "cycle bicycle 自行车") { Icons.Rounded.PedalBike }
            add(g, "soccer", "football sport 足球") { Icons.Rounded.SportsSoccer }
            add(g, "basketball", "sport 篮球") { Icons.Rounded.SportsBasketball }
            add(g, "pool", "swim 游泳") { Icons.Rounded.Pool }
            add(g, "hiking", "walk outdoor 徒步") { Icons.Rounded.Hiking }
            add(g, "meditation", "yoga mind self 冥想") { Icons.Rounded.SelfImprovement }
            add(g, "flight", "plane travel airline 飞机") { Icons.Rounded.Flight }
            add(g, "hotel", "bed travel 酒店") { Icons.Rounded.Hotel }
            add(g, "restaurant", "food dining meal 餐厅") { Icons.Rounded.Restaurant }
            add(g, "coffee", "cafe drink 咖啡") { Icons.Rounded.LocalCafe }
            add(g, "shopping_cart", "shop buy cart 购物") { Icons.Rounded.ShoppingCart }
            add(g, "luggage", "travel trip 行李") { Icons.Rounded.Luggage }
            add(g, "school", "education study course 学习") { Icons.Rounded.School }
            add(g, "theater", "comedy show entertainment 娱乐") { Icons.Rounded.TheaterComedy }
            add(g, "celebration", "party event 庆祝") { Icons.Rounded.Celebration }
            add(g, "golf", "sport 高尔夫") { Icons.Rounded.GolfCourse }
            add(g, "skiing", "snow sport 滑雪") { Icons.Rounded.DownhillSkiing }
            add(g, "delivery", "food takeout scooter 外卖") { Icons.Rounded.DeliveryDining }
        }
        IconGroup.APPS.let { g ->
            add(g, "chat", "message im 聊天") { Icons.AutoMirrored.Rounded.Chat }
            add(g, "mail", "email inbox 邮件") { Icons.Rounded.Mail }
            add(g, "phone", "call mobile 电话") { Icons.Rounded.Phone }
            add(g, "cloud", "storage sync drive 云") { Icons.Rounded.Cloud }
            add(g, "storage", "disk drive 存储") { Icons.Rounded.Storage }
            add(g, "vpn", "key proxy network 代理") { Icons.Rounded.VpnKey }
            add(g, "globe", "web internet browser 网站") { Icons.Rounded.Public }
            add(g, "code", "developer programming 代码") { Icons.Rounded.Code }
            add(g, "terminal", "shell console server 终端") { Icons.Rounded.Terminal }
            add(g, "sparkle", "ai magic auto 智能") { Icons.Rounded.AutoAwesome }
            add(g, "robot", "ai bot assistant 机器人") { Icons.Rounded.SmartToy }
            add(g, "camera", "photo picture 相机") { Icons.Rounded.PhotoCamera }
            add(g, "map", "navigation location 地图") { Icons.Rounded.Map }
            add(g, "calendar", "date schedule 日历") { Icons.Rounded.CalendarMonth }
            add(g, "translate", "language dictionary 翻译") { Icons.Rounded.Translate }
            add(g, "password", "login credential 密码") { Icons.Rounded.Password }
            add(g, "shield", "protect antivirus 安全") { Icons.Rounded.Shield }
            add(g, "wifi", "internet network broadband 网络") { Icons.Rounded.Wifi }
            add(g, "devices", "computer phone tablet 设备") { Icons.Rounded.Devices }
            add(g, "apps", "grid application 应用") { Icons.Rounded.Apps }
            add(g, "android", "google play 安卓") { Icons.Rounded.Android }
            add(g, "design", "brush art palette 设计") { Icons.Rounded.Palette }
        }
        IconGroup.CATEGORIES.let { g ->
            add(g, "movie", "video film streaming 视频") { Icons.Rounded.Movie }
            add(g, "music", "song audio streaming 音乐") { Icons.Rounded.MusicNote }
            add(g, "gamepad", "game gaming console 游戏") { Icons.Rounded.SportsEsports }
            add(g, "work", "office productivity briefcase 工作") { Icons.Rounded.Work }
            add(g, "build", "tool utility wrench 工具") { Icons.Rounded.Build }
            add(g, "newspaper", "news magazine 新闻") { Icons.Rounded.Newspaper }
            add(g, "spa", "lifestyle wellness 生活") { Icons.Rounded.Spa }
            add(g, "category", "other misc 其他") { Icons.Rounded.Category }
            add(g, "home", "house rent utility 家") { Icons.Rounded.Home }
            add(g, "car", "auto vehicle transport 汽车") { Icons.Rounded.DirectionsCar }
            add(g, "pets", "dog cat animal 宠物") { Icons.Rounded.Pets }
            add(g, "health", "hospital medical insurance 医疗") { Icons.Rounded.LocalHospital }
            add(g, "medication", "pill medicine pharmacy 药") { Icons.Rounded.Medication }
            add(g, "child", "baby kid family 儿童") { Icons.Rounded.ChildCare }
            add(g, "grocery", "food store supermarket 超市") { Icons.Rounded.LocalGroceryStore }
            add(g, "clothes", "fashion wardrobe 服装") { Icons.Rounded.Checkroom }
            add(g, "bolt", "electricity power energy 电") { Icons.Rounded.Bolt }
            add(g, "water", "drop utility bill 水") { Icons.Rounded.WaterDrop }
            add(g, "router", "internet broadband isp 宽带") { Icons.Rounded.Router }
            add(g, "phone_android", "mobile plan carrier 手机") { Icons.Rounded.PhoneAndroid }
        }
        IconGroup.DOCUMENTS.let { g ->
            add(g, "description", "document file 文档") { Icons.Rounded.Description }
            add(g, "article", "blog post read 文章") { Icons.AutoMirrored.Rounded.Article }
            add(g, "book", "reading ebook library 书") { Icons.AutoMirrored.Rounded.MenuBook }
            add(g, "folder", "files directory 文件夹") { Icons.Rounded.Folder }
            add(g, "receipt", "bill invoice 账单") { Icons.AutoMirrored.Rounded.ReceiptLong }
            add(g, "inventory", "box archive 归档") { Icons.Rounded.Inventory2 }
            add(g, "note", "edit memo 笔记") { Icons.Rounded.EditNote }
            add(g, "contract", "law legal 合同") { Icons.Rounded.Gavel }
            add(g, "print", "printer 打印") { Icons.Rounded.Print }
            add(g, "attach", "attachment clip 附件") { Icons.Rounded.AttachFile }
        }
        IconGroup.MARKERS.let { g ->
            add(g, "star", "favorite rating 星") { Icons.Rounded.Star }
            add(g, "heart", "love favorite like 喜欢") { Icons.Rounded.Favorite }
            add(g, "flag", "mark report 旗") { Icons.Rounded.Flag }
            add(g, "bookmark", "save mark 书签") { Icons.Rounded.Bookmark }
            add(g, "label", "tag mark 标签") { Icons.AutoMirrored.Rounded.Label }
            add(g, "pin", "push pin 置顶") { Icons.Rounded.PushPin }
            add(g, "place", "location marker 位置") { Icons.Rounded.Place }
            add(g, "eye", "visibility watch 查看") { Icons.Rounded.Visibility }
            add(g, "lightbulb", "idea tip 灵感") { Icons.Rounded.Lightbulb }
            add(g, "bell", "notification reminder 提醒") { Icons.Rounded.Notifications }
            add(g, "alarm", "clock reminder 闹钟") { Icons.Rounded.Alarm }
            add(g, "schedule", "time clock 时间") { Icons.Rounded.Schedule }
        }
        IconGroup.NATURE.let { g ->
            add(g, "tree", "park nature 树") { Icons.Rounded.Park }
            add(g, "sun", "weather sunny day 太阳") { Icons.Rounded.WbSunny }
            add(g, "moon", "night dark 月亮") { Icons.Rounded.DarkMode }
            add(g, "storm", "weather thunder rain 雷雨") { Icons.Rounded.Thunderstorm }
            add(g, "eco", "leaf green environment 环保") { Icons.Rounded.Eco }
            add(g, "waves", "sea ocean water 海") { Icons.Rounded.Waves }
            add(g, "forest", "woods trees 森林") { Icons.Rounded.Forest }
            add(g, "flower", "florist plant 花") { Icons.Rounded.LocalFlorist }
            add(g, "fire", "flame hot 火") { Icons.Rounded.LocalFireDepartment }
            add(g, "snow", "cold winter ac 雪") { Icons.Rounded.AcUnit }
            add(g, "grass", "plant garden 草") { Icons.Rounded.Grass }
        }
        IconGroup.SHAPES.let { g ->
            add(g, "circle", "round dot 圆") { Icons.Rounded.Circle }
            add(g, "square", "box 方") { Icons.Rounded.Square }
            add(g, "hexagon", "shape 六边形") { Icons.Rounded.Hexagon }
            add(g, "pentagon", "shape 五边形") { Icons.Rounded.Pentagon }
            add(g, "triangle", "shape delta 三角") { Icons.Rounded.ChangeHistory }
            add(g, "diamond", "gem premium 钻石") { Icons.Rounded.Diamond }
            add(g, "star_outline", "shape star 星形") { Icons.Rounded.StarOutline }
            add(g, "interests", "shapes mixed 形状") { Icons.Rounded.Interests }
            add(g, "blur", "dots pattern 点阵") { Icons.Rounded.BlurOn }
            add(g, "token", "hex coin 代币") { Icons.Rounded.Token }
        }
        IconGroup.SYMBOLS.let { g ->
            add(g, "check", "done ok success 完成") { Icons.Rounded.CheckCircle }
            add(g, "cancel", "close remove 取消") { Icons.Rounded.Cancel }
            add(g, "add", "plus new 添加") { Icons.Rounded.AddCircle }
            add(g, "info", "about detail 信息") { Icons.Rounded.Info }
            add(g, "help", "question faq 帮助") { Icons.AutoMirrored.Rounded.Help }
            add(g, "warning", "alert caution 警告") { Icons.Rounded.Warning }
            add(g, "error", "problem alert 错误") { Icons.Rounded.Error }
            add(g, "lock", "secure private 锁") { Icons.Rounded.Lock }
            add(g, "key", "access license 钥匙") { Icons.Rounded.Key }
            add(g, "tag", "hash number 井号") { Icons.Rounded.Tag }
            add(g, "percent", "discount rate 百分比") { Icons.Rounded.Percent }
            add(g, "infinity", "lifetime forever unlimited 永久") { Icons.Rounded.AllInclusive }
            add(g, "loop", "repeat recurring 循环") { Icons.Rounded.Loop }
            add(g, "sync", "refresh update 同步") { Icons.Rounded.Sync }
            add(g, "verified", "badge official 认证") { Icons.Rounded.Verified }
            add(g, "premium", "award vip member 会员") { Icons.Rounded.WorkspacePremium }
            add(g, "qr_code", "scan code 二维码") { Icons.Rounded.QrCode }
            add(g, "rocket", "launch fast 火箭") { Icons.Rounded.RocketLaunch }
        }
        IconGroup.SYSTEM.let { g ->
            add(g, "settings", "gear preferences 设置") { Icons.Rounded.Settings }
            add(g, "tune", "adjust sliders 调整") { Icons.Rounded.Tune }
            add(g, "person", "user account profile 用户") { Icons.Rounded.Person }
            add(g, "group", "people team shared 群组") { Icons.Rounded.Group }
            add(g, "family", "household members 家庭") { Icons.Rounded.FamilyRestroom }
            add(g, "language", "world locale 语言") { Icons.Rounded.Language }
            add(g, "search", "find lookup 搜索") { Icons.Rounded.Search }
            add(g, "dashboard", "overview panel 仪表盘") { Icons.Rounded.Dashboard }
            add(g, "widgets", "blocks modules 组件") { Icons.Rounded.Widgets }
            add(g, "battery", "power charge 电池") { Icons.Rounded.BatteryFull }
            add(g, "memory", "chip cpu hardware 芯片") { Icons.Rounded.Memory }
            add(g, "server", "dns hosting 服务器") { Icons.Rounded.Dns }
            add(g, "security", "shield protection 安全") { Icons.Rounded.Security }
            add(g, "backup", "cloud upload 备份") { Icons.Rounded.Backup }
            add(g, "download", "save get 下载") { Icons.Rounded.Download }
            add(g, "upload", "send put 上传") { Icons.Rounded.Upload }
            add(g, "link", "url chain 链接") { Icons.Rounded.Link }
            add(g, "share", "send social 分享") { Icons.Rounded.Share }
        }
        IconGroup.MEDIA.let { g ->
            add(g, "tv", "television streaming 电视") { Icons.Rounded.Tv }
            add(g, "headphones", "audio music 耳机") { Icons.Rounded.Headphones }
            add(g, "podcast", "audio show 播客") { Icons.Rounded.Podcasts }
            add(g, "radio", "fm audio 广播") { Icons.Rounded.Radio }
            add(g, "mic", "microphone voice record 麦克风") { Icons.Rounded.Mic }
            add(g, "play", "video start 播放") { Icons.Rounded.PlayCircle }
            add(g, "video_library", "collection videos 视频库") { Icons.Rounded.VideoLibrary }
            add(g, "music_library", "collection songs 音乐库") { Icons.Rounded.LibraryMusic }
            add(g, "photo_library", "gallery pictures 相册") { Icons.Rounded.PhotoLibrary }
            add(g, "live_tv", "broadcast stream 直播") { Icons.Rounded.LiveTv }
            add(g, "album", "record vinyl 专辑") { Icons.Rounded.Album }
            add(g, "theaters", "cinema film 影院") { Icons.Rounded.Theaters }
            add(g, "stories", "audiobook reading 有声书") { Icons.Rounded.AutoStories }
            add(g, "videocam", "camera record 摄像") { Icons.Rounded.Videocam }
        }
        IconGroup.FINANCE.let { g ->
            add(g, "wallet", "money balance 钱包") { Icons.Rounded.AccountBalanceWallet }
            add(g, "bank", "account institution 银行") { Icons.Rounded.AccountBalance }
            add(g, "credit_card", "card payment 信用卡") { Icons.Rounded.CreditCard }
            add(g, "payments", "cash bills 支付") { Icons.Rounded.Payments }
            add(g, "dollar", "money usd currency 美元") { Icons.Rounded.AttachMoney }
            add(g, "euro", "money eur currency 欧元") { Icons.Rounded.Euro }
            add(g, "yen", "money cny jpy rmb currency 人民币 日元") { Icons.Rounded.CurrencyYen }
            add(g, "pound", "money gbp currency 英镑") { Icons.Rounded.CurrencyPound }
            add(g, "bitcoin", "crypto btc currency 比特币") { Icons.Rounded.CurrencyBitcoin }
            add(g, "savings", "piggy bank save 储蓄") { Icons.Rounded.Savings }
            add(g, "trending_up", "growth chart increase 增长") { Icons.AutoMirrored.Rounded.TrendingUp }
            add(g, "bar_chart", "statistics analytics 图表") { Icons.Rounded.BarChart }
            add(g, "pie_chart", "statistics share 饼图") { Icons.Rounded.PieChart }
            add(g, "shopping_bag", "purchase buy 购物袋") { Icons.Rounded.ShoppingBag }
            add(g, "store", "shop market 商店") { Icons.Rounded.Store }
            add(g, "gift", "redeem present 礼物") { Icons.Rounded.Redeem }
            add(g, "sell", "price tag 价格") { Icons.Rounded.Sell }
            add(g, "offer", "discount coupon deal 优惠") { Icons.Rounded.LocalOffer }
            add(g, "coins", "token points 积分") { Icons.Rounded.Toll }
        }
        return list
    }
}
