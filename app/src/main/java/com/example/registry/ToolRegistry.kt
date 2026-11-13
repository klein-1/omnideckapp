package com.example.registry

import com.example.design.ToolCategory

object ToolRegistry {
    val ALL_TOOLS: List<ToolMeta> = listOf(
        // ==================== 1. MEDIA & MUSIC DOWNLOADER ====================
        ToolMeta(
            id = "dl-video", number = 1, chapterId = 1, category = ToolCategory.DOWNLOAD,
            name = "Video & Playlist Downloader",
            tagline = "YouTube, Instagram, X & playlists (max quality/4K, subs, templates, queue).",
            keywords = listOf("seal", "youtube", "instagram", "twitter", "x", "video", "download", "playlist", "4k", "subtitles", "yt-dlp", "aria2", "cookies", "proxy"),
            requires = setOf(CapabilityRequirement.NETWORK, CapabilityRequirement.STORAGE), offline = false,
            io = ToolIO(accepts = setOf(DataKind.URL, DataKind.TEXT), produces = setOf(DataKind.VIDEO, DataKind.TEXT)),
            glyphKey = "ch_fetch"
        ),
        ToolMeta(
            id = "dl-music", number = 2, chapterId = 2, category = ToolCategory.DOWNLOAD,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Music & Album Downloader",
            tagline = "Spotify, YT Music & SoundCloud tracks/playlists in FLAC, MP3, M4A & OPUS.",
            keywords = listOf("spotify", "spotdl", "music", "audio", "mp3", "flac", "m4a", "opus", "album", "playlist", "songs", "id3", "cover"),
            requires = setOf(CapabilityRequirement.NETWORK, CapabilityRequirement.STORAGE), offline = false,
            io = ToolIO(accepts = setOf(DataKind.URL, DataKind.TEXT), produces = setOf(DataKind.AUDIO, DataKind.TEXT)),
            glyphKey = "ch_tunes"
        ),

        // ==================== 2. URL SAFETY CHECKER ====================
        ToolMeta(
            id = "url-safety", number = 3, chapterId = 3, category = ToolCategory.URL,
            secondaryCategories = setOf(ToolCategory.SOCIAL, ToolCategory.DEV),
            name = "URL Safety & Unshortener",
            tagline = "Multi-hop redirect tracer, TLS cert audit, punycode check & tracker stripper.",
            keywords = listOf("url", "link", "checker", "safety", "unshorten", "bitly", "redirect", "phishing", "ssl", "tls", "utm", "cleaner", "virustotal"),
            requires = setOf(CapabilityRequirement.NETWORK), offline = false,
            io = ToolIO(accepts = setOf(DataKind.URL, DataKind.TEXT), produces = setOf(DataKind.URL, DataKind.TEXT)),
            glyphKey = "ch_link"
        ),

        // ==================== 3. LOCAL HTTP/HTTPS SERVER (SHTTPS) ====================
        ToolMeta(
            id = "srv-shttps", number = 4, chapterId = 4, category = ToolCategory.SERVER,
            secondaryCategories = setOf(ToolCategory.DEV),
            name = "Local HTTP / HTTPS Server",
            tagline = "Host any device folder over LAN Wi-Fi with TLS cert, web UI, auth & logs.",
            keywords = listOf("shttps", "http", "https", "server", "local", "lan", "host", "fileshare", "upload", "web", "ssl", "tls", "echo", "webhook"),
            requires = setOf(CapabilityRequirement.NETWORK, CapabilityRequirement.STORAGE), offline = true,
            io = ToolIO(produces = setOf(DataKind.URL, DataKind.TEXT)),
            glyphKey = "ch_serve"
        ),

        // ==================== 4. IMAGE STUDIO (IMAGETOOLBOX PARITY) ====================
        ToolMeta(
            id = "img-filters", number = 5, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Image Filters & Effects",
            tagline = "17+ filters, composable filter chains, dither, duotone & QR filter sharing.",
            keywords = listOf("imagetoolbox", "filter", "brightness", "contrast", "saturation", "blur", "sharpen", "dither", "duotone", "vignette", "sepia", "photo"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(accepts = setOf(DataKind.IMAGE, DataKind.TEXT), produces = setOf(DataKind.IMAGE, DataKind.JSON)),
            glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-crop", number = 6, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Crop, Rotate & Blur-Pad",
            tagline = "Aspect-ratio crop (1:1, 16:9, 4:3), 90° rotate, flip & blurred padding.",
            keywords = listOf("crop", "rotate", "flip", "aspect", "pad", "frame", "image"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-resize", number = 7, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Image Resizer",
            tagline = "Resize by exact dimensions, percentage scale or social presets.",
            keywords = listOf("resize", "scale", "dimensions", "pixels", "dpi", "shrink", "image"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-compress", number = 8, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Image Compressor & Converter",
            tagline = "Convert JPEG/PNG/WEBP with quality slider and live byte-size preview.",
            keywords = listOf("compress", "convert", "jpeg", "png", "webp", "reduce", "size", "kb", "mb", "image"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-draw", number = 9, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Drawing Board & Privacy Blur",
            tagline = "Freehand sketchpad, markup & censor/pixelate brush over photos.",
            keywords = listOf("draw", "drawing", "board", "sketch", "paint", "censor", "blur", "pixelate", "privacy", "markup"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-bg", number = 10, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Background Remover",
            tagline = "Chroma & edge tolerance cutout exporting transparent PNG.",
            keywords = listOf("background", "remover", "cutout", "transparent", "png", "chroma", "erase"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-collage", number = 11, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Image Stitcher & Collage",
            tagline = "Stitch multiple photos vertically, horizontally or in 2×2 grid.",
            keywords = listOf("stitch", "collage", "grid", "combine", "join", "panorama", "images"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-gif", number = 12, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Animated GIF Maker",
            tagline = "Encode multi-photo sequences into looping GIF89a with frame delay control.",
            keywords = listOf("gif", "animate", "animation", "frames", "loop", "maker"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-watermark", number = 13, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Watermark Adder",
            tagline = "Stamp single corner or tiled diagonal watermarks with opacity control.",
            keywords = listOf("watermark", "stamp", "copyright", "brand", "overlay", "image"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-exif", number = 14, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "EXIF Metadata & Scrubber",
            tagline = "Inspect camera, ISO, lens & GPS tags or strip all metadata cleanly.",
            keywords = listOf("exif", "metadata", "gps", "privacy", "scrub", "strip", "camera", "iso"),
            io = ToolIO(produces = setOf(DataKind.TEXT, DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-ocr", number = 15, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "OCR & Document Scan Enhance",
            tagline = "Adaptive contrast document scanner & text extraction helper.",
            keywords = listOf("ocr", "scan", "text", "extract", "document", "receipt", "camera"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-textcard", number = 16, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "Text to Image Poster",
            tagline = "Render formatted typography quotes and notes as high-res PNG cards.",
            keywords = listOf("text to image", "poster", "card", "quote", "render", "png"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-sig", number = 17, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA, ToolCategory.PDF),
            name = "Signature Maker",
            tagline = "Draw crisp transparent digital signatures ready to save or stamp on PDFs.",
            keywords = listOf("signature", "sign", "autograph", "transparent", "png", "draw"),
            io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_image"
        ),
        ToolMeta(
            id = "img-colorpick", number = 18, chapterId = 5, category = ToolCategory.IMAGE,
            secondaryCategories = setOf(ToolCategory.MEDIA, ToolCategory.COMMON),
            name = "Image Color Detector",
            tagline = "Tap any pixel on a photo or camera image to read HEX, RGB & HSL.",
            keywords = listOf("color picker", "color detector", "eyedropper", "hex", "rgb", "hsl", "pixel"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_image"
        ),

        // ==================== 5. PDF LAB (PAPERKNIFE PARITY) ====================
        ToolMeta(
            id = "pdf-merge", number = 19, chapterId = 6, category = ToolCategory.PDF,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "PDF Merger",
            tagline = "Combine multiple PDF documents in custom order offline.",
            keywords = listOf("paperknife", "pdf", "merge", "combine", "join", "documents"),
            io = ToolIO(accepts = setOf(DataKind.PDF), produces = setOf(DataKind.PDF)), glyphKey = "ch_pdf"
        ),
        ToolMeta(
            id = "pdf-split", number = 20, chapterId = 6, category = ToolCategory.PDF,
            name = "PDF Splitter & Page Organizer",
            tagline = "Visual page thumbnails: reorder, rotate, delete & split ranges.",
            keywords = listOf("pdf", "split", "organize", "reorder", "rotate", "delete", "pages", "extract"),
            io = ToolIO(accepts = setOf(DataKind.PDF), produces = setOf(DataKind.PDF)), glyphKey = "ch_pdf"
        ),
        ToolMeta(
            id = "pdf-compress", number = 21, chapterId = 6, category = ToolCategory.PDF,
            name = "PDF Compressor",
            tagline = "Shrink PDF file size using DPI & quality presets with byte comparison.",
            keywords = listOf("pdf", "compress", "shrink", "reduce", "size", "optimize"),
            io = ToolIO(accepts = setOf(DataKind.PDF), produces = setOf(DataKind.PDF)), glyphKey = "ch_pdf"
        ),
        ToolMeta(
            id = "pdf-convert", number = 22, chapterId = 6, category = ToolCategory.PDF,
            secondaryCategories = setOf(ToolCategory.MEDIA),
            name = "PDF ↔ Images & Creator",
            tagline = "Convert multi-selected photos into PDF or render PDF pages as PNGs.",
            keywords = listOf("pdf", "creator", "images to pdf", "pdf to image", "png", "convert"),
            io = ToolIO(accepts = setOf(DataKind.IMAGE, DataKind.PDF), produces = setOf(DataKind.PDF, DataKind.IMAGE)), glyphKey = "ch_pdf"
        ),
        ToolMeta(
            id = "pdf-annotate", number = 23, chapterId = 6, category = ToolCategory.PDF,
            name = "PDF Sign, Redact & Watermark",
            tagline = "Black-out sensitive regions, stamp signature, watermark & page numbers.",
            keywords = listOf("pdf", "sign", "redact", "censor", "watermark", "page numbers", "annotate"),
            io = ToolIO(accepts = setOf(DataKind.PDF), produces = setOf(DataKind.PDF)), glyphKey = "ch_pdf"
        ),
        ToolMeta(
            id = "pdf-text", number = 24, chapterId = 6, category = ToolCategory.PDF,
            name = "PDF Text & Metadata",
            tagline = "Decompress FlateDecode text streams & inspect/scrub document metadata.",
            keywords = listOf("pdf", "text", "extract", "metadata", "title", "author", "scrub"),
            io = ToolIO(accepts = setOf(DataKind.PDF), produces = setOf(DataKind.TEXT)), glyphKey = "ch_pdf"
        ),

        // ==================== 6. DATE & TIME ====================
        ToolMeta(
            id = "time-analog", number = 25, chapterId = 7, category = ToolCategory.TIME,
            name = "Analog & Digital Clock",
            tagline = "Swiss railway dial analog clock with live sweeping seconds & 24h digital readout.",
            keywords = listOf("analog", "digital", "clock", "watch", "time", "seconds"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_time"
        ),
        ToolMeta(
            id = "time-world", number = 26, chapterId = 7, category = ToolCategory.TIME,
            name = "Time Zone Converter",
            tagline = "Live multi-city world clock with interactive offset scrubber.",
            keywords = listOf("timezone", "world clock", "utc", "gmt", "ist", "est", "pst", "jst", "meeting"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_time"
        ),
        ToolMeta(
            id = "time-calendar", number = 27, chapterId = 7, category = ToolCategory.TIME,
            name = "Calendar & Date Diff",
            tagline = "Monthly grid calendar, date-to-date duration & add/subtract days.",
            keywords = listOf("calendar", "date", "difference", "days", "month", "week"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_time"
        ),
        ToolMeta(
            id = "time-watch", number = 28, chapterId = 7, category = ToolCategory.TIME,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Stopwatch & Countdown Timer",
            tagline = "Millisecond lap stopwatch & multi-preset countdown timer.",
            keywords = listOf("stopwatch", "timer", "countdown", "lap", "split", "alarm"),
            io = ToolIO(produces = setOf(DataKind.CSV, DataKind.TEXT)), glyphKey = "ch_time"
        ),
        ToolMeta(
            id = "time-pomodoro", number = 29, chapterId = 7, category = ToolCategory.TIME,
            name = "Pomodoro Focus Timer",
            tagline = "25/5/15 work-break cycles with completed session counter & chime.",
            keywords = listOf("pomodoro", "focus", "study", "work", "interval", "timer"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_time"
        ),
        ToolMeta(
            id = "time-tally", number = 30, chapterId = 7, category = ToolCategory.TIME,
            name = "Tally Counter",
            tagline = "Persistent multi-counter bench with custom steps, goals & lap CSV.",
            keywords = listOf("tally", "counter", "clicker", "count", "score", "reps", "laps"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(produces = setOf(DataKind.CSV, DataKind.TEXT)), glyphKey = "tool_tally"
        ),
        ToolMeta(
            id = "time-age", number = 31, chapterId = 7, category = ToolCategory.TIME,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Age to Seconds & Epoch",
            tagline = "Exact live age down to seconds, next birthday countdown & Unix epoch converter.",
            keywords = listOf("age", "birthday", "seconds", "epoch", "unix", "timestamp"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_time"
        ),

        // ==================== 7. CALCULATOR & CHARTS ====================
        ToolMeta(
            id = "calc-basic", number = 32, chapterId = 8, category = ToolCategory.CALC,
            name = "Standard Calculator",
            tagline = "Clean large-key arithmetic calculator with tape history.",
            keywords = listOf("calculator", "basic", "arithmetic", "add", "multiply", "divide"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-sci", number = 33, chapterId = 8, category = ToolCategory.CALC,
            name = "Scientific Calculator",
            tagline = "Full trig, inverse/hyperbolic, logs, combinatorics, factorial, constants & Deg/Rad.",
            keywords = listOf("scientific", "calculator", "sin", "cos", "tan", "sinh", "asin", "log", "ln", "factorial", "ncr", "npr", "sqrt", "math"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-bmi", number = 34, chapterId = 8, category = ToolCategory.CALC,
            secondaryCategories = setOf(ToolCategory.HEALTH),
            name = "BMI Calculator",
            tagline = "Body Mass Index (metric & ft/in) with WHO healthy weight range.",
            keywords = listOf("bmi", "weight", "height", "health", "body", "fitness"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-pct", number = 35, chapterId = 8, category = ToolCategory.CALC,
            name = "Discount & Percentage Suite",
            tagline = "Discounts, stacked % off, % change, markup/margin & reverse percentage.",
            keywords = listOf("discount", "percent", "percentage", "sale", "margin", "markup"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-auto", number = 36, chapterId = 8, category = ToolCategory.CALC,
            name = "Fuel & Mileage Calculator",
            tagline = "Trip fuel cost, km/L & MPG efficiency & split cost per passenger.",
            keywords = listOf("fuel", "mileage", "petrol", "diesel", "mpg", "kml", "trip", "car"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-elec", number = 37, chapterId = 8, category = ToolCategory.CALC,
            name = "Electricity Bill Calculator",
            tagline = "Estimate daily/monthly kWh consumption & tariff cost from appliance watts.",
            keywords = listOf("electricity", "power", "watt", "kwh", "energy", "bill", "tariff"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-linechart", number = 38, chapterId = 8, category = ToolCategory.CALC,
            name = "Line Chart Maker",
            tagline = "Plot interactive multi-point XY line graphs from your data & export PNG.",
            keywords = listOf("line chart", "graph", "plot", "data", "analytics", "chart"),
            io = ToolIO(produces = setOf(DataKind.IMAGE, DataKind.CSV)), glyphKey = "ch_calc"
        ),
        ToolMeta(
            id = "calc-piechart", number = 39, chapterId = 8, category = ToolCategory.CALC,
            name = "Pie & Donut Chart Maker",
            tagline = "Build proportional pie/donut charts with percentage labels & PNG export.",
            keywords = listOf("pie chart", "donut", "graph", "chart", "percentage", "slice"),
            io = ToolIO(produces = setOf(DataKind.IMAGE, DataKind.CSV)), glyphKey = "ch_calc"
        ),

        // ==================== 8. FINANCE ====================
        ToolMeta(
            id = "fin-invoice", number = 40, chapterId = 9, category = ToolCategory.FINANCE,
            name = "Invoice Generator (PDF)",
            tagline = "Create itemized tax invoices with client details and export crisp PDF.",
            keywords = listOf("invoice", "bill", "receipt", "pdf", "business", "tax", "client"),
            io = ToolIO(produces = setOf(DataKind.PDF, DataKind.TEXT)), glyphKey = "ch_money"
        ),
        ToolMeta(
            id = "fin-interest", number = 41, chapterId = 9, category = ToolCategory.FINANCE,
            name = "Simple & Compound Interest",
            tagline = "Compare SI vs CI across daily/monthly/quarterly/annual compounding.",
            keywords = listOf("interest", "simple", "compound", "investment", "return"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_money"
        ),
        ToolMeta(
            id = "fin-mf", number = 42, chapterId = 9, category = ToolCategory.FINANCE,
            name = "Mutual Funds (SIP & Lumpsum)",
            tagline = "Project SIP monthly wealth creation and lumpsum CAGR maturity.",
            keywords = listOf("mutual fund", "sip", "lumpsum", "cagr", "wealth", "invest"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_money"
        ),
        ToolMeta(
            id = "fin-emi", number = 43, chapterId = 9, category = ToolCategory.FINANCE,
            name = "EMI & Group Bill Split",
            tagline = "Loan EMI principal vs interest breakdown + multi-person tip splitter.",
            keywords = listOf("emi", "loan", "mortgage", "tip", "split", "bill"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_money"
        ),
        ToolMeta(
            id = "fin-gst", number = 44, chapterId = 9, category = ToolCategory.FINANCE,
            name = "GST & Tax Calculator",
            tagline = "Inclusive/exclusive GST slabs (3/5/12/18/28%) with CGST & SGST split.",
            keywords = listOf("gst", "tax", "vat", "cgst", "sgst", "slab"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_money"
        ),
        ToolMeta(
            id = "fin-fdrd", number = 45, chapterId = 9, category = ToolCategory.FINANCE,
            name = "FD & RD Calculator",
            tagline = "Fixed Deposit & Recurring Deposit maturity with quarterly compounding.",
            keywords = listOf("fd", "rd", "fixed deposit", "recurring deposit", "bank", "maturity"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_money"
        ),
        ToolMeta(
            id = "fin-expense", number = 46, chapterId = 9, category = ToolCategory.FINANCE,
            name = "Expense Tracker",
            tagline = "SQLite income & expense ledger starting clean with CSV export.",
            keywords = listOf("expense", "tracker", "budget", "ledger", "income", "spending"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(produces = setOf(DataKind.CSV, DataKind.TEXT)), glyphKey = "ch_money"
        ),

        // ==================== 9. ESSENTIALS ====================
        ToolMeta(
            id = "ess-flash", number = 47, chapterId = 10, category = ToolCategory.ESSENTIALS,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Multi-Mode Flashlight",
            tagline = "LED torch, SOS Morse beacon, adjustable strobe Hz & screen light.",
            keywords = listOf("flashlight", "torch", "sos", "strobe", "led", "light"),
            requires = setOf(CapabilityRequirement.CAMERA), glyphKey = "ch_essentials"
        ),
        ToolMeta(
            id = "ess-qrscan", number = 48, chapterId = 10, category = ToolCategory.ESSENTIALS,
            name = "QR & Barcode Scanner",
            tagline = "Live CameraX scanner + photo picker decoder for QR, EAN, UPC & Code128.",
            keywords = listOf("qr", "scanner", "barcode", "scan", "camera", "ean", "code128"),
            requires = setOf(CapabilityRequirement.CAMERA),
            io = ToolIO(produces = setOf(DataKind.TEXT, DataKind.URL)), glyphKey = "ch_essentials"
        ),
        ToolMeta(
            id = "ess-dict", number = 49, chapterId = 10, category = ToolCategory.ESSENTIALS,
            name = "Dictionary & Thesaurus",
            tagline = "Word definitions, phonetics, parts of speech & synonyms lookup.",
            keywords = listOf("dictionary", "define", "meaning", "thesaurus", "synonyms", "words"),
            requires = setOf(CapabilityRequirement.NETWORK), offline = false,
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_essentials"
        ),
        ToolMeta(
            id = "ess-translate", number = 50, chapterId = 10, category = ToolCategory.ESSENTIALS,
            secondaryCategories = setOf(ToolCategory.TEXT),
            name = "Language Translator",
            tagline = "Multi-language text translation with TTS playback & quick phrasebook.",
            keywords = listOf("translate", "translator", "language", "spanish", "french", "hindi", "japanese", "arabic"),
            requires = setOf(CapabilityRequirement.NETWORK), offline = false,
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_essentials"
        ),
        ToolMeta(
            id = "ess-todo", number = 51, chapterId = 10, category = ToolCategory.ESSENTIALS,
            name = "To-Do List & Manager",
            tagline = "Task manager with priorities, filters & SQLite persistence.",
            keywords = listOf("todo", "tasks", "checklist", "manager", "reminders", "productivity"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_essentials"
        ),
        ToolMeta(
            id = "ess-notes", number = 52, chapterId = 10, category = ToolCategory.ESSENTIALS,
            name = "Quick Notes",
            tagline = "Minimal offline scratchpad and pinned notes saved locally.",
            keywords = listOf("notes", "memo", "scratchpad", "jot", "write"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_essentials"
        ),
        ToolMeta(
            id = "ess-shop", number = 53, chapterId = 10, category = ToolCategory.ESSENTIALS,
            name = "Shopping List",
            tagline = "Grocery & shopping list with quantities, prices & live basket total.",
            keywords = listOf("shopping", "grocery", "list", "cart", "budget", "buy"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_essentials"
        ),

        // ==================== 10. MEASUREMENT ====================
        ToolMeta(
            id = "meas-ruler", number = 54, chapterId = 11, category = ToolCategory.MEASURE,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Calibrated Ruler",
            tagline = "DPI-calibrated cm/mm/inch screen ruler with manual card calibration.",
            keywords = listOf("ruler", "measure", "cm", "mm", "inch", "scale"),
            glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-protractor", number = 55, chapterId = 11, category = ToolCategory.MEASURE,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Protractor & Angle Meter",
            tagline = "Interactive touch angle protractor + live pitch tilt inclinometer.",
            keywords = listOf("protractor", "angle", "degree", "inclinometer", "tilt"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-level", number = 56, chapterId = 11, category = ToolCategory.MEASURE,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Spirit & Digital Level",
            tagline = "2D bubble level & axis roll/pitch readout with zero-calibration.",
            keywords = listOf("spirit level", "bubble", "digital level", "plumb", "balance", "tilt"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-vibro", number = 57, chapterId = 11, category = ToolCategory.MEASURE,
            name = "Vibrometer",
            tagline = "Live accelerometer waveform, Peak G, RMS & Mercalli vibration modes.",
            keywords = listOf("vibrometer", "seismometer", "vibration", "earthquake", "g-force", "waveform"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-pedometer", number = 58, chapterId = 11, category = ToolCategory.MEASURE,
            secondaryCategories = setOf(ToolCategory.HEALTH),
            name = "Pedometer & Step Counter",
            tagline = "Hardware step sensor + accel stride tracker with distance & kcal.",
            keywords = listOf("pedometer", "steps", "walk", "run", "distance", "fitness"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-speedtest", number = 59, chapterId = 11, category = ToolCategory.MEASURE,
            secondaryCategories = setOf(ToolCategory.DEVICE),
            name = "Internet Speed Test",
            tagline = "Real HTTP download throughput, latency ping & jitter benchmark.",
            keywords = listOf("speed test", "internet", "mbps", "bandwidth", "latency", "network"),
            requires = setOf(CapabilityRequirement.NETWORK), offline = false, glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-pendulum", number = 60, chapterId = 11, category = ToolCategory.MEASURE,
            name = "Pendulum Lab",
            tagline = "Period stopwatch, beat tapper, length-to-period solver & live swing sim.",
            keywords = listOf("pendulum", "physics", "period", "gravity", "oscillation", "beat"),
            glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-sound", number = 61, chapterId = 11, category = ToolCategory.MEASURE,
            secondaryCategories = setOf(ToolCategory.COMMON),
            name = "Sound / Decibel Meter",
            tagline = "Live microphone PCM dB SPL meter with waveform, peak hold & noise guide.",
            keywords = listOf("sound meter", "decibel", "db", "noise", "mic", "audio", "spl"),
            requires = setOf(CapabilityRequirement.MIC), glyphKey = "ch_measure"
        ),
        ToolMeta(
            id = "meas-light", number = 62, chapterId = 11, category = ToolCategory.MEASURE,
            name = "Light Meter (Lux)",
            tagline = "Ambient light sensor lux readout with min/max & workspace EV guide.",
            keywords = listOf("light meter", "lux", "brightness", "illumination", "sensor", "ev"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_measure"
        ),

        // ==================== 11. COMMON ====================
        ToolMeta(
            id = "com-mirror", number = 63, chapterId = 12, category = ToolCategory.COMMON,
            name = "Camera Mirror",
            tagline = "Front-camera mirror with brightness ring border, zoom & freeze-frame.",
            keywords = listOf("mirror", "selfie", "front camera", "reflection", "freeze"),
            requires = setOf(CapabilityRequirement.CAMERA), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-compass", number = 64, chapterId = 12, category = ToolCategory.COMMON,
            name = "Magnetic Compass",
            tagline = "True-north declination bearing dial using accelerometer + magnetometer.",
            keywords = listOf("compass", "north", "bearing", "azimuth", "direction", "magnetometer"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-units", number = 65, chapterId = 12, category = ToolCategory.COMMON,
            secondaryCategories = setOf(ToolCategory.CALC),
            name = "Unit Converter",
            tagline = "Length, mass, temp, data, speed, time, volume & regional land units.",
            keywords = listOf("unit", "converter", "km", "miles", "kg", "lbs", "celsius", "bigha", "kanal", "marla", "acre"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-currency", number = 66, chapterId = 12, category = ToolCategory.COMMON,
            secondaryCategories = setOf(ToolCategory.FINANCE),
            name = "Currency Converter",
            tagline = "30+ world currencies with live ECB/Frankfurter sync & offline rate edit.",
            keywords = listOf("currency", "exchange", "usd", "inr", "eur", "gbp", "jpy", "forex"),
            requires = setOf(CapabilityRequirement.NETWORK),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-freq", number = 67, chapterId = 12, category = ToolCategory.COMMON,
            name = "Sound Frequency Generator",
            tagline = "Real-time PCM tone synthesizer (20 Hz–20 kHz, Sine/Square/Triangle/Saw).",
            keywords = listOf("frequency", "tone", "sound", "hz", "sine", "oscillator", "audio"),
            glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-metal", number = 68, chapterId = 12, category = ToolCategory.COMMON,
            name = "Metal Detector",
            tagline = "Live 3-axis magnetometer microtesla (µT) spike detector with alert chime.",
            keywords = listOf("metal detector", "magnet", "tesla", "emf", "magnetic", "sensor"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-gpscam", number = 69, chapterId = 12, category = ToolCategory.COMMON,
            name = "GPS Location Camera",
            tagline = "Capture photos stamped with live GPS latitude, longitude & timestamp.",
            keywords = listOf("gps camera", "location", "stamp", "geotag", "photo", "coordinates"),
            requires = setOf(CapabilityRequirement.CAMERA, CapabilityRequirement.GEO), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-magnifier", number = 70, chapterId = 12, category = ToolCategory.COMMON,
            name = "Digital Magnifier",
            tagline = "High-zoom rear camera loupe with torch, freeze & high-contrast filter.",
            keywords = listOf("magnifier", "zoom", "loupe", "reading", "camera"),
            requires = setOf(CapabilityRequirement.CAMERA), glyphKey = "ch_common"
        ),
        ToolMeta(
            id = "com-speedo", number = 71, chapterId = 12, category = ToolCategory.COMMON,
            name = "GPS Speedometer",
            tagline = "Real GNSS ground speed in km/h, mph, knots, peak speed & altitude.",
            keywords = listOf("speedometer", "speed", "gps", "kmh", "mph", "knots", "odometer"),
            requires = setOf(CapabilityRequirement.GEO), glyphKey = "ch_common"
        ),

        // ==================== 12. TEXT TOOLS ====================
        ToolMeta(
            id = "txt-case", number = 72, chapterId = 13, category = ToolCategory.TEXT,
            name = "Case Converter",
            tagline = "15 editorial & developer casings (Title, Sentence, camel, snake, kebab).",
            keywords = listOf("case", "uppercase", "lowercase", "camel", "snake", "kebab", "title"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "tool_case"
        ),
        ToolMeta(
            id = "txt-diff", number = 73, chapterId = 13, category = ToolCategory.TEXT,
            name = "Word Counter & Text Diff",
            tagline = "Words, chars, reading time, LCS line diff, regex replace & sort/dedupe.",
            keywords = listOf("word counter", "diff", "compare", "characters", "lines", "sort", "dedupe"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"
        ),
        ToolMeta(
            id = "txt-morse", number = 74, chapterId = 13, category = ToolCategory.TEXT,
            name = "Morse, Binary & Base64",
            tagline = "Text ↔ Morse with tone playback, Binary, Base64, URL & ROT13.",
            keywords = listOf("morse", "binary", "base64", "encode", "decode", "rot13", "url"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"
        ),
        ToolMeta(
            id = "txt-aes", number = 75, chapterId = 13, category = ToolCategory.TEXT,
            secondaryCategories = setOf(ToolCategory.DEV),
            name = "AES-256-GCM Text Encryption",
            tagline = "Encrypt & decrypt messages locally using PBKDF2 + AES-256-GCM.",
            keywords = listOf("encrypt", "decrypt", "aes", "gcm", "cipher", "secret", "security"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_vault"
        ),
        ToolMeta(
            id = "txt-vault", number = 76, chapterId = 22, category = ToolCategory.TEXT,
            secondaryCategories = setOf(ToolCategory.DEV),
            name = "Encrypted Password Vault",
            tagline = "Zero-knowledge Room vault protected by your master passphrase.",
            keywords = listOf("password vault", "vault", "secrets", "credentials", "lock", "keychain"),
            requires = setOf(CapabilityRequirement.STORAGE),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_vault"
        ),
        ToolMeta(
            id = "txt-repeat", number = 77, chapterId = 13, category = ToolCategory.TEXT,
            secondaryCategories = setOf(ToolCategory.SOCIAL),
            name = "Text Repeater, Stylizer & Art",
            tagline = "Repeat text N×, apply 9 Unicode fancy fonts & generate ASCII banners.",
            keywords = listOf("repeater", "stylish text", "fancy font", "ascii art", "text art", "banner", "bio"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"
        ),
        ToolMeta(
            id = "txt-lorem", number = 78, chapterId = 13, category = ToolCategory.TEXT,
            name = "Lorem Ipsum Generator",
            tagline = "Generate clean editorial filler paragraphs for layouts and mockups.",
            keywords = listOf("lorem", "ipsum", "placeholder", "dummy", "paragraphs", "text"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"
        ),
        ToolMeta(
            id = "txt-markdown", number = 79, chapterId = 13, category = ToolCategory.TEXT,
            secondaryCategories = setOf(ToolCategory.DEV),
            name = "Markdown Previewer",
            tagline = "Live formatted Markdown renderer with heading, list, code & HTML export.",
            keywords = listOf("markdown", "md", "preview", "html", "editor", "render"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"
        ),
        ToolMeta(
            id = "txt-tts", number = 80, chapterId = 13, category = ToolCategory.TEXT,
            name = "Text to Speech Studio",
            tagline = "Android TTS engine with adjustable pitch, speed & WAV file synthesis.",
            keywords = listOf("tts", "text to speech", "voice", "speak", "read aloud", "wav"),
            io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.AUDIO)), glyphKey = "ch_words"
        ),

        // ==================== 13. MEDIA (AUDIO / VIDEO) ====================
        ToolMeta(
            id = "med-audiorec", number = 81, chapterId = 14, category = ToolCategory.MEDIA,
            name = "Audio Recorder",
            tagline = "M4A voice & field recorder with live mic meter & built-in playback.",
            keywords = listOf("audio recorder", "voice", "microphone", "record", "m4a", "memo"),
            requires = setOf(CapabilityRequirement.MIC),
            io = ToolIO(produces = setOf(DataKind.AUDIO)), glyphKey = "ch_studio"
        ),
        ToolMeta(
            id = "med-audiotrim", number = 82, chapterId = 14, category = ToolCategory.MEDIA,
            name = "Audio Trimmer",
            tagline = "Pick any audio file & trim start/end timestamps cleanly via MediaMuxer.",
            keywords = listOf("audio trim", "cut", "crop", "ringtone", "mp3", "m4a", "aac"),
            io = ToolIO(produces = setOf(DataKind.AUDIO)), glyphKey = "ch_studio"
        ),
        ToolMeta(
            id = "med-vid2aud", number = 83, chapterId = 14, category = ToolCategory.MEDIA,
            name = "Video to Audio Extractor",
            tagline = "Extract lossless audio tracks from picked videos offline via MediaExtractor.",
            keywords = listOf("video to audio", "extract audio", "mp4 to m4a", "demux", "soundtrack"),
            io = ToolIO(produces = setOf(DataKind.AUDIO)), glyphKey = "ch_studio"
        ),
        ToolMeta(
            id = "med-vidcomp", number = 84, chapterId = 14, category = ToolCategory.MEDIA,
            name = "Video Inspector & Trimmer",
            tagline = "Inspect resolution/bitrate/codec & trim or strip audio from video clips.",
            keywords = listOf("video compressor", "video trim", "mute video", "bitrate", "codec", "mp4"),
            io = ToolIO(produces = setOf(DataKind.VIDEO)), glyphKey = "ch_studio"
        ),

        // ==================== 14. DEVICE INFO ====================
        ToolMeta(
            id = "devinfo-sys", number = 85, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Device, CPU, GPU, RAM & Root",
            tagline = "SoC cores, /proc/cpuinfo, live RAM heap, OpenGL GPU, storage & root check.",
            keywords = listOf("device info", "cpu", "ram", "gpu", "storage", "root checker", "specs", "model"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_device"
        ),
        ToolMeta(
            id = "devinfo-bat", number = 86, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Battery & Thermal Monitor",
            tagline = "Charge %, voltage mV, battery temp °C, health, tech & thermal status.",
            keywords = listOf("battery", "thermal", "temperature", "voltage", "charging", "health"),
            io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_device"
        ),
        ToolMeta(
            id = "devinfo-sensors", number = 87, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Hardware Sensor Matrix",
            tagline = "Enumerate all device sensors & stream live XYZ axis telemetry.",
            keywords = listOf("sensors", "accelerometer", "gyroscope", "magnetometer", "barometer", "hardware"),
            requires = setOf(CapabilityRequirement.MOTION), glyphKey = "ch_device"
        ),
        ToolMeta(
            id = "devinfo-net", number = 88, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Wi-Fi, Ping & GPS Extractor",
            tagline = "Wi-Fi RSSI/link speed/IP, TCP Ping latency tester & GPS coordinate extractor.",
            keywords = listOf("wifi", "ping", "latency", "location extractor", "ip", "network", "gps"),
            requires = setOf(CapabilityRequirement.NETWORK, CapabilityRequirement.GEO), glyphKey = "ch_device"
        ),
        ToolMeta(
            id = "devinfo-bt", number = 89, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Bluetooth LE Scanner",
            tagline = "Scan nearby BLE beacons/devices with RSSI signal strength & MAC.",
            keywords = listOf("bluetooth", "ble", "scanner", "rssi", "devices", "wireless"),
            requires = setOf(CapabilityRequirement.BLUETOOTH), glyphKey = "ch_device"
        ),
        ToolMeta(
            id = "devinfo-screen", number = 90, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Dead Pixel & Multi-Touch Test",
            tagline = "Fullscreen color cycler for stuck pixels + multi-finger touch canvas.",
            keywords = listOf("dead pixel", "screen test", "display", "multi-touch", "touchscreen"),
            glyphKey = "ch_device"
        ),
        ToolMeta(
            id = "devinfo-speaker", number = 91, chapterId = 15, category = ToolCategory.DEVICE,
            name = "Speaker Cleaner",
            tagline = "165 Hz resonant low-frequency sweep to clear water/dust from speakers.",
            keywords = listOf("speaker cleaner", "water eject", "audio", "hz", "clean"),
            glyphKey = "ch_device"
        ),

        // ==================== 15. GAMES ====================
        ToolMeta(id = "game-2048", number = 92, chapterId = 16, category = ToolCategory.GAMES, name = "2048", tagline = "Sliding number puzzle with swipe/D-pad & undo.", keywords = listOf("2048", "puzzle", "tiles", "game"), glyphKey = "ch_play"),
        ToolMeta(id = "game-ttt", number = 93, chapterId = 16, category = ToolCategory.GAMES, name = "Tic-Tac-Toe", tagline = "Pass-and-play or unbeatable Minimax AI.", keywords = listOf("tic tac toe", "xo", "noughts", "crosses", "game"), glyphKey = "ch_play"),
        ToolMeta(id = "game-rps", number = 94, chapterId = 16, category = ToolCategory.GAMES, name = "Rock Paper Scissors", tagline = "Classic showdown vs AI with streak & score tracking.", keywords = listOf("rock", "paper", "scissors", "rps", "game"), glyphKey = "ch_play"),
        ToolMeta(id = "game-tower", number = 95, chapterId = 16, category = ToolCategory.GAMES, name = "Tower Block Stacker", tagline = "Precision timing block drop with overhang slicing.", keywords = listOf("tower", "stack", "blocks", "arcade", "timing"), glyphKey = "ch_play"),
        ToolMeta(id = "game-cricket", number = 96, chapterId = 16, category = ToolCategory.GAMES, name = "Cricket Scorer", tagline = "Ball-by-ball runs, wickets, overs, extras & strike rate.", keywords = listOf("cricket", "score", "runs", "overs", "wickets", "sports"), glyphKey = "ch_play"),
        ToolMeta(id = "game-dice", number = 97, chapterId = 16, category = ToolCategory.GAMES, name = "Coin Toss & Dice Roller", tagline = "Animated coin flip + multi-dice D4..D20 RPG roller.", keywords = listOf("coin", "toss", "flip", "dice", "d20", "d6", "rpg"), glyphKey = "ch_play"),
        ToolMeta(id = "game-brain", number = 98, chapterId = 16, category = ToolCategory.GAMES, name = "Brain Math Blitz", tagline = "30-second rapid mental arithmetic streak challenge.", keywords = listOf("brain", "math", "quiz", "blitz", "mental"), glyphKey = "ch_play"),
        ToolMeta(id = "game-tug", number = 99, chapterId = 16, category = ToolCategory.GAMES, name = "2P Tug of War", tagline = "Split-screen rapid-tap battle for two players.", keywords = listOf("tug of war", "2 player", "multiplayer", "tap", "party"), glyphKey = "ch_play"),
        ToolMeta(id = "game-wheel", number = 100, chapterId = 16, category = ToolCategory.GAMES, name = "Lucky Wheel & Spin Bottle", tagline = "Custom choice wheel + party Spin the Bottle spinner.", keywords = listOf("lucky wheel", "spin", "bottle", "roulette", "randomizer"), glyphKey = "ch_play"),
        ToolMeta(id = "game-quiz", number = 101, chapterId = 16, category = ToolCategory.GAMES, name = "World Capitals Quiz", tagline = "Test your geography across 30+ countries with streak save.", keywords = listOf("world quiz", "capitals", "geography", "trivia", "countries"), glyphKey = "ch_play"),
        ToolMeta(id = "game-mines", number = 102, chapterId = 16, category = ToolCategory.GAMES, name = "Minesweeper", tagline = "Classic 9×9 / 12×12 grid with flood-fill & flag toggle.", keywords = listOf("minesweeper", "mines", "puzzle", "flags"), glyphKey = "ch_play"),
        ToolMeta(id = "game-sudoku", number = 103, chapterId = 16, category = ToolCategory.GAMES, name = "Sudoku", tagline = "Real backtracking puzzles (Easy/Med/Hard) with check & hint.", keywords = listOf("sudoku", "numbers", "9x9", "logic", "puzzle"), glyphKey = "ch_play"),
        ToolMeta(id = "game-memory", number = 104, chapterId = 16, category = ToolCategory.GAMES, name = "Memory Card Match", tagline = "Flip and match pairs with move counter & best score.", keywords = listOf("memory", "cards", "match", "pairs"), glyphKey = "ch_play"),
        ToolMeta(id = "game-snake", number = 105, chapterId = 16, category = ToolCategory.GAMES, name = "Snake", tagline = "Retro arcade Snake with D-pad & swipe controls.", keywords = listOf("snake", "arcade", "retro", "game"), glyphKey = "ch_play"),
        ToolMeta(id = "game-hangman", number = 106, chapterId = 16, category = ToolCategory.GAMES, name = "Hangman", tagline = "Word guessing puzzle with crisp Canvas gallows.", keywords = listOf("hangman", "word", "guess", "letters"), glyphKey = "ch_play"),
        ToolMeta(id = "game-chess", number = 107, chapterId = 16, category = ToolCategory.GAMES, name = "Chess (2P & AI)", tagline = "Full legal rules (castling, en passant, promotion, check) vs AI or 2P.", keywords = listOf("chess", "checkmate", "board", "strategy", "ai"), glyphKey = "ch_play"),

        // ==================== 16. HEALTH ====================
        ToolMeta(id = "hlth-breathe", number = 108, chapterId = 17, category = ToolCategory.HEALTH, name = "Breathe Control", tagline = "Guided 4-4-4-4 box & 4-7-8 breathing cadence ring with haptics.", keywords = listOf("breathe", "meditation", "box breathing", "calm", "stress"), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-water", number = 109, chapterId = 17, category = ToolCategory.HEALTH, name = "Water Tracker", tagline = "Log real daily ml hydration toward your custom goal in SQLite.", keywords = listOf("water", "hydration", "drink", "ml", "tracker"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-meds", number = 110, chapterId = 17, category = ToolCategory.HEALTH, name = "Medicine Reminder", tagline = "Manage daily medication schedules & mark doses taken.", keywords = listOf("medicine", "pill", "reminder", "dosage", "health"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-hr", number = 111, chapterId = 17, category = ToolCategory.HEALTH, name = "Heart Rate Monitor", tagline = "Tap-tempo BPM analyzer & optical pulse estimation log.", keywords = listOf("heart rate", "bpm", "pulse", "cardio"), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-calorie", number = 112, chapterId = 17, category = ToolCategory.HEALTH, name = "Calorie & Macro Calculator", tagline = "Mifflin-St Jeor BMR, TDEE activity levels & macro gram split.", keywords = listOf("calorie", "tdee", "bmr", "macros", "protein", "diet"), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-sleep", number = 113, chapterId = 17, category = ToolCategory.HEALTH, name = "Sleep Cycle & Log", tagline = "90-minute sleep cycle bedtime planner & local sleep log.", keywords = listOf("sleep", "cycles", "bedtime", "wake", "rest"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-eye", number = 114, chapterId = 17, category = ToolCategory.HEALTH, name = "Eye Exercise 20-20-20", tagline = "20-minute screen break timer + guided eye movement drills.", keywords = listOf("eye", "exercise", "20-20-20", "vision", "strain"), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-bp", number = 115, chapterId = 17, category = ToolCategory.HEALTH, name = "Blood Pressure Log", tagline = "Record systolic/diastolic readings with AHA stage classification.", keywords = listOf("blood pressure", "bp", "systolic", "diastolic", "hypertension"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),

        // ==================== 17. HUMAN BENCHMARK ====================
        ToolMeta(id = "bench-reaction", number = 116, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Reaction Time", tagline = "5-round millisecond visual reflex benchmark with personal best.", keywords = listOf("reaction", "reflex", "ms", "benchmark", "human benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-sequence", number = 117, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Sequence Memory", tagline = "Memorize progressively longer flashing 3×3 tile sequences.", keywords = listOf("sequence", "memory", "simon", "pattern", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-aim", number = 118, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Aim Trainer", tagline = "Hit 30 moving targets cleanly; measures average ms per target.", keywords = listOf("aim", "trainer", "targets", "precision", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-number", number = 119, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Number Memory", tagline = "Recall growing multi-digit numbers shown for a brief flash.", keywords = listOf("number memory", "digits", "recall", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-verbal", number = 120, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Verbal Memory", tagline = "Track whether each word is NEW or SEEN across 3 lives.", keywords = listOf("verbal memory", "words", "seen", "new", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-chimp", number = 121, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Chimp Test", tagline = "Tap numbered squares in order after they conceal on tap 1.", keywords = listOf("chimp", "working memory", "numbers", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-visual", number = 122, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Visual Memory", tagline = "Recall expanding NxN highlighted spatial tile matrices.", keywords = listOf("visual memory", "grid", "tiles", "spatial", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-typing", number = 123, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Typing Speed Test", tagline = "Live WPM & character accuracy test on clean prose passages.", keywords = listOf("typing", "wpm", "speed", "keyboard", "accuracy", "benchmark"), glyphKey = "ch_mind"),

        // ==================== 18. SOCIAL & WEB ====================
        ToolMeta(id = "soc-wa", number = 124, chapterId = 18, category = ToolCategory.SOCIAL, name = "WhatsApp Direct", tagline = "Message any phone number directly without saving it to contacts.", keywords = listOf("whatsapp", "direct", "wa", "chat", "phone", "message"), glyphKey = "ch_social"),
        ToolMeta(id = "soc-caption", number = 125, chapterId = 18, category = ToolCategory.SOCIAL, name = "Caption, Hashtag & Bio Styler", tagline = "Clean social line-break formatter, hashtag sets & bio character limits.", keywords = listOf("caption", "hashtag", "instagram", "bio", "social", "formatter"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-ytdesc", number = 126, chapterId = 18, category = ToolCategory.SOCIAL, name = "YT Description & Chapters", tagline = "Build timestamped YouTube chapters, links & structured descriptions.", keywords = listOf("youtube", "description", "chapters", "timestamps", "video"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-status", number = 127, chapterId = 18, category = ToolCategory.SOCIAL, name = "Status Folder Viewer (SAF)", tagline = "Browse & copy saved statuses from any user-picked folder via SAF.", keywords = listOf("status", "downloader", "whatsapp status", "saf", "media"), glyphKey = "ch_social"),
        ToolMeta(id = "soc-wifiqr", number = 128, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.COMMON), name = "Wi-Fi QR Share Card", tagline = "Generate scannable WPA/WPA3 Wi-Fi login QR codes cleanly.", keywords = listOf("wifi", "qr", "share", "network", "password"), glyphKey = "ch_social"),
        ToolMeta(id = "soc-pfp", number = 129, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.IMAGE), name = "Profile Picture Border Maker", tagline = "Crop circular avatar & add crisp solid accent rings for PNG export.", keywords = listOf("profile picture", "pfp", "avatar", "border", "circle", "ring"), io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_social"),

        // ==================== 19. DEVELOPER ====================
        ToolMeta(id = "dev-hash", number = 130, chapterId = 19, category = ToolCategory.DEV, name = "Hash, HMAC & PBKDF2", tagline = "14 digests (SHA-2/3, MD5, CRC32, Murmur3), HMAC, PBKDF2 & constant-time verify.", keywords = listOf("hash", "md5", "sha256", "sha512", "sha3", "crc32", "hmac", "pbkdf2"), io = ToolIO(accepts = setOf(DataKind.TEXT, DataKind.JSON, DataKind.URL), produces = setOf(DataKind.HASH, DataKind.TEXT)), glyphKey = "tool_hash"),
        ToolMeta(id = "dev-json", number = 131, chapterId = 19, category = ToolCategory.DEV, name = "JSON Formatter & JWT Decoder", tagline = "Pretty-print, minify, sort keys & inspect JWT headers/claims.", keywords = listOf("json", "format", "minify", "jwt", "token", "decode"), io = ToolIO(accepts = setOf(DataKind.TEXT, DataKind.JSON), produces = setOf(DataKind.JSON, DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-pass", number = 132, chapterId = 22, category = ToolCategory.DEV, secondaryCategories = setOf(ToolCategory.COMMON), name = "Password, UUID & QR", tagline = "CSPRNG password/passphrase builder, UUID v4 & live QR matrix.", keywords = listOf("password", "passphrase", "uuid", "guid", "qr", "qrcode", "generator"), io = ToolIO(accepts = setOf(DataKind.TEXT, DataKind.URL), produces = setOf(DataKind.TEXT)), glyphKey = "ch_vault"),
        ToolMeta(id = "dev-color", number = 133, chapterId = 5, category = ToolCategory.DEV, secondaryCategories = setOf(ToolCategory.IMAGE, ToolCategory.COMMON), name = "Color & Contrast Studio", tagline = "HEX/RGB/HSL mixer, WCAG AA/AAA contrast checker & shade ramp.", keywords = listOf("color", "hex", "rgb", "hsl", "contrast", "wcag", "palette"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_image"),
        ToolMeta(id = "dev-regex", number = 134, chapterId = 19, category = ToolCategory.DEV, name = "Regex Tester & CSS Generator", tagline = "Live regex matcher/replacer + CSS shadow, border & flexbox code builder.", keywords = listOf("regex", "regexp", "css", "shadow", "flexbox", "pattern", "match"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-cron", number = 135, chapterId = 19, category = ToolCategory.DEV, name = "Cron Expression Builder", tagline = "Visual 5-field cron builder with next 5 execution timestamps.", keywords = listOf("cron", "schedule", "timer", "crontab", "interval"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-dns", number = 136, chapterId = 19, category = ToolCategory.DEV, name = "DNS Lookup (DoH)", tagline = "Query live A, AAAA, MX, TXT, CNAME & NS records via DNS-over-HTTPS.", keywords = listOf("dns", "doh", "nslookup", "mx", "txt", "cname", "ip"), requires = setOf(CapabilityRequirement.NETWORK), offline = false, io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-sandbox", number = 137, chapterId = 19, category = ToolCategory.DEV, name = "Formula & Script Sandbox", tagline = "Multi-line variable evaluator with math functions and live step trace.", keywords = listOf("compiler", "runner", "sandbox", "script", "eval", "variables", "code"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),

        // ==================== EXPANSION TOOLS (138..170) ====================
        // Text (+6)
        ToolMeta(id = "txt-cipher", number = 138, chapterId = 13, category = ToolCategory.TEXT, name = "Classical Ciphers Suite", tagline = "Caesar (+25 brute force), Vigenere, Atbash & Rail Fence cipher workbench.", keywords = listOf("caesar", "vigenere", "atbash", "rail fence", "cipher", "crypto", "brute force"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"),
        ToolMeta(id = "txt-stego", number = 139, chapterId = 13, category = ToolCategory.TEXT, secondaryCategories = setOf(ToolCategory.DEV), name = "Zero-Width Steganography", tagline = "Hide & extract invisible secret messages inside ordinary carrier text.", keywords = listOf("steganography", "zero width", "hidden", "invisible", "secret", "unicode"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"),
        ToolMeta(id = "txt-slug", number = 140, chapterId = 13, category = ToolCategory.TEXT, secondaryCategories = setOf(ToolCategory.DEV), name = "Slug, HTML & Punycode", tagline = "URL slugify, HTML entities, Unicode \\uXXXX escape & IDN punycode.", keywords = listOf("slug", "html", "entities", "unicode", "punycode", "idn", "escape"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"),
        ToolMeta(id = "txt-lines", number = 141, chapterId = 13, category = ToolCategory.TEXT, name = "Line Master & Extractors", tagline = "Sort, shuffle, dedupe, number lines or extract all emails, URLs & IPs.", keywords = listOf("sort", "dedupe", "lines", "extract", "emails", "urls", "ips", "shuffle"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"),
        ToolMeta(id = "txt-phonetic", number = 142, chapterId = 13, category = ToolCategory.TEXT, name = "NATO, Braille & Leetspeak", tagline = "Convert phrases to NATO aviation phonetic alphabet, Braille or Leetspeak.", keywords = listOf("nato", "phonetic", "braille", "leet", "alpha bravo"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_words"),
        ToolMeta(id = "txt-csvjson", number = 143, chapterId = 13, category = ToolCategory.TEXT, secondaryCategories = setOf(ToolCategory.DEV), name = "CSV ↔ JSON & MD Table", tagline = "Convert CSV rows into formatted JSON arrays or Markdown tables offline.", keywords = listOf("csv", "json", "markdown", "table", "tsv", "convert"), io = ToolIO(accepts = setOf(DataKind.TEXT, DataKind.CSV, DataKind.JSON), produces = setOf(DataKind.JSON, DataKind.TEXT)), glyphKey = "ch_words"),

        // Media & Audio (+6)
        ToolMeta(id = "med-tuner", number = 144, chapterId = 14, category = ToolCategory.MEDIA, name = "Chromatic Instrument Tuner", tagline = "Live microphone pitch autocorrelation to nearest musical note & cents.", keywords = listOf("tuner", "guitar", "pitch", "hz", "chromatic", "audio"), requires = setOf(CapabilityRequirement.MIC), glyphKey = "ch_studio"),
        ToolMeta(id = "med-metronome", number = 145, chapterId = 14, category = ToolCategory.MEDIA, name = "Precision BPM Metronome", tagline = "30–240 BPM acoustic woodblock click with tap tempo & bar beat accents.", keywords = listOf("metronome", "bpm", "tempo", "tap", "rhythm", "music"), glyphKey = "ch_studio"),
        ToolMeta(id = "med-noise", number = 146, chapterId = 14, category = ToolCategory.MEDIA, name = "Noise Generator (Focus/Sleep)", tagline = "Real-time White, Pink & Brown noise PCM generator with auto-sleep timer.", keywords = listOf("white noise", "pink noise", "brown noise", "sleep", "focus", "ambient"), glyphKey = "ch_studio"),
        ToolMeta(id = "med-dtmf", number = 147, chapterId = 14, category = ToolCategory.MEDIA, name = "DTMF Dual-Tone Dialer", tagline = "Synthesize authentic ITU dual-tone phone keypad frequencies live.", keywords = listOf("dtmf", "dialer", "tone", "keypad", "audio", "telecom"), glyphKey = "ch_studio"),
        ToolMeta(id = "med-wavsynth", number = 148, chapterId = 14, category = ToolCategory.MEDIA, name = "WAV Reverser & Speed FX", tagline = "Load any PCM WAV file to reverse playback, 1.5x speed up or add echo.", keywords = listOf("wav", "reverse", "echo", "speed", "audio fx"), io = ToolIO(produces = setOf(DataKind.AUDIO)), glyphKey = "ch_studio"),
        ToolMeta(id = "med-framegrab", number = 149, chapterId = 14, category = ToolCategory.MEDIA, secondaryCategories = setOf(ToolCategory.IMAGE), name = "Video Frame Grabber", tagline = "Scrub any video by timestamp and export crisp still PNG frames.", keywords = listOf("video", "frame", "screenshot", "still", "grab", "mp4"), io = ToolIO(produces = setOf(DataKind.IMAGE)), glyphKey = "ch_studio"),

        // Games (+5)
        ToolMeta(id = "game-c4", number = 150, chapterId = 16, category = ToolCategory.GAMES, name = "Connect Four", tagline = "Classic 6×7 four-in-a-row strategy game vs tactical AI or 2P.", keywords = listOf("connect 4", "connect four", "board", "strategy", "ai"), glyphKey = "ch_play"),
        ToolMeta(id = "game-wordle", number = 151, chapterId = 16, category = ToolCategory.GAMES, name = "5-Letter Wordle", tagline = "6-guess daily-style 5-letter word puzzle with color-coded keyboard.", keywords = listOf("wordle", "word", "guess", "puzzle", "letters"), glyphKey = "ch_play"),
        ToolMeta(id = "game-bricks", number = 152, chapterId = 16, category = ToolCategory.GAMES, name = "Brick Breaker", tagline = "60fps arcade paddle & ball physics with multi-row destructible bricks.", keywords = listOf("brick breaker", "breakout", "arkanoid", "arcade", "paddle"), glyphKey = "ch_play"),
        ToolMeta(id = "game-lights", number = 153, chapterId = 16, category = ToolCategory.GAMES, name = "Lights Out 5×5", tagline = "Classic toggle logic grid with mathematically exact optimal Hint solver.", keywords = listOf("lights out", "puzzle", "logic", "grid", "toggle"), glyphKey = "ch_play"),
        ToolMeta(id = "game-checkers", number = 154, chapterId = 16, category = ToolCategory.GAMES, name = "Checkers (Draughts 8×8)", tagline = "Full rules with mandatory capture, multi-jumps, kings & AI opponent.", keywords = listOf("checkers", "draughts", "board", "strategy", "ai"), glyphKey = "ch_play"),

        // Health (+4)
        ToolMeta(id = "hlth-fast", number = 155, chapterId = 17, category = ToolCategory.HEALTH, name = "Intermittent Fasting Timer", tagline = "16:8 / 18:6 / OMAD tracker with live metabolic phase readout & SQLite log.", keywords = listOf("fasting", "16:8", "omad", "ketosis", "autophagy", "health"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-bodyfat", number = 156, chapterId = 17, category = ToolCategory.HEALTH, secondaryCategories = setOf(ToolCategory.CALC), name = "US Navy Body Fat %", tagline = "Estimate body fat percentage, fat mass & lean body mass from measurements.", keywords = listOf("body fat", "navy", "waist", "lean mass", "fitness"), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-hiit", number = 157, chapterId = 17, category = ToolCategory.HEALTH, name = "HIIT / Tabata Timer", tagline = "Custom Warmup, Work, Rest & Rounds interval timer with audio cue beeps.", keywords = listOf("hiit", "tabata", "interval", "workout", "emom", "fitness"), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-caffeine", number = 158, chapterId = 17, category = ToolCategory.HEALTH, name = "Caffeine Half-Life Tracker", tagline = "Track 24h caffeine intake vs 400mg cap with 5.7h clearance curve.", keywords = listOf("caffeine", "coffee", "tea", "half life", "sleep", "tracker"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),

        // Social & Web (+6)
        ToolMeta(id = "soc-utm", number = 159, chapterId = 18, category = ToolCategory.SOCIAL, name = "UTM Campaign Link Builder", tagline = "Construct tagged marketing URLs (source/medium/campaign) with instant QR.", keywords = listOf("utm", "campaign", "marketing", "analytics", "url", "qr"), io = ToolIO(produces = setOf(DataKind.URL, DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-og", number = 160, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.DEV), name = "OpenGraph & Meta Builder", tagline = "Generate OpenGraph, Twitter Card social preview tags & robots.txt.", keywords = listOf("opengraph", "seo", "meta", "twitter card", "robots.txt", "html"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-vcard", number = 161, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.COMMON), name = "vCard Digital Contact QR", tagline = "Build standard v3.0 .vcf digital business cards with scannable QR.", keywords = listOf("vcard", "vcf", "contact", "business card", "qr"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-curl", number = 162, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.DEV), name = "HTTP / REST & cURL Tester", tagline = "Execute real GET/POST/PUT/DELETE HTTP requests or generate cURL snippets.", keywords = listOf("http", "rest", "curl", "api", "webhook", "post", "get"), requires = setOf(CapabilityRequirement.NETWORK), offline = false, io = ToolIO(produces = setOf(DataKind.TEXT, DataKind.JSON)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-deeplink", number = 163, chapterId = 18, category = ToolCategory.SOCIAL, name = "Deep-Link Action Composer", tagline = "Compose mailto:, tel:, sms: & geo: URIs with QR & Android intent launcher.", keywords = listOf("deep link", "mailto", "tel", "sms", "geo", "uri", "intent"), io = ToolIO(produces = setOf(DataKind.URL, DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-biopage", number = 164, chapterId = 18, category = ToolCategory.SOCIAL, name = "Link-in-Bio HTML Generator", tagline = "Design a responsive single-file Link-in-Bio HTML page ready to host.", keywords = listOf("link in bio", "html", "micro page", "links", "creator"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),

        // Benchmark (+4)
        ToolMeta(id = "bench-color", number = 165, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Color Shade Perception", tagline = "Spot the subtle odd HSL tile as grid size grows & contrast delta narrows.", keywords = listOf("color vision", "shade", "hue", "perception", "eye test", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-stroop", number = 166, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Stroop Cognitive Interference", tagline = "Tap ink color while ignoring conflicting word names across 12 timed trials.", keywords = listOf("stroop", "cognitive", "focus", "psychology", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-audio", number = 167, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Auditory Reflex Test", tagline = "Measure pure ear-to-finger millisecond reaction to synthesized 1kHz tone.", keywords = listOf("audio reaction", "auditory", "reflex", "sound", "ms", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-cps", number = 168, chapterId = 20, category = ToolCategory.BENCHMARK, name = "CPS Tap Speed Sprint", tagline = "Measure peak Clicks-Per-Second across 1s, 5s & 10s timed sprints.", keywords = listOf("cps", "click speed", "tap speed", "jitter", "benchmark"), glyphKey = "ch_mind"),

        // Developer (+2)
        ToolMeta(id = "dev-hmacsig", number = 169, chapterId = 19, category = ToolCategory.DEV, secondaryCategories = setOf(ToolCategory.TEXT), name = "ECDSA / RSA Keypair & Sign", tagline = "Generate real ECDSA P-256 or RSA-2048 keys, PEM export, sign & verify.", keywords = listOf("ecdsa", "rsa", "keypair", "signature", "pem", "crypto", "verify"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-batchid", number = 170, chapterId = 19, category = ToolCategory.DEV, name = "ULID / NanoID & Entropy Lab", tagline = "Batch-generate sortable ULIDs, UUIDv4, NanoIDs & analyze Shannon entropy.", keywords = listOf("ulid", "nanoid", "uuid", "entropy", "shannon", "batch", "id"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),

        // ==================== EXPANSION PACK II (171..194) ====================
        // Games (+12)
        ToolMeta(id = "game-solitaire", number = 171, chapterId = 16, category = ToolCategory.GAMES, name = "Klondike Solitaire", tagline = "Classic 7-column Klondike tableau, stock/waste & 4 suit foundations.", keywords = listOf("solitaire", "klondike", "cards", "patience", "deck"), glyphKey = "ch_play"),
        ToolMeta(id = "game-blackjack", number = 172, chapterId = 16, category = ToolCategory.GAMES, name = "Blackjack 21", tagline = "Hit, Stand & Double Down vs Dealer soft-17 rules with chip bankroll.", keywords = listOf("blackjack", "21", "cards", "dealer", "casino", "chips"), glyphKey = "ch_play"),
        ToolMeta(id = "game-fifteen", number = 173, chapterId = 16, category = ToolCategory.GAMES, name = "15-Puzzle Slider", tagline = "4×4 sliding tile puzzle with mathematically guaranteed solvable shuffles.", keywords = listOf("15 puzzle", "sliding puzzle", "tiles", "4x4", "logic"), glyphKey = "ch_play"),
        ToolMeta(id = "game-hanoi", number = 174, chapterId = 16, category = ToolCategory.GAMES, name = "Tower of Hanoi", tagline = "Classic 3-peg disk puzzle (3–7 disks) with optimal move counter.", keywords = listOf("hanoi", "tower", "disks", "pegs", "logic", "puzzle"), glyphKey = "ch_play"),
        ToolMeta(id = "game-reversi", number = 175, chapterId = 16, category = ToolCategory.GAMES, name = "Reversi (Othello)", tagline = "8×8 disc-flipping strategy board game vs AI or 2-player pass.", keywords = listOf("reversi", "othello", "board", "strategy", "flips", "ai"), glyphKey = "ch_play"),
        ToolMeta(id = "game-dots", number = 176, chapterId = 16, category = ToolCategory.GAMES, name = "Dots & Boxes", tagline = "4×4 box-capture grid with chain-capture tactical AI or 2P.", keywords = listOf("dots and boxes", "grid", "2 player", "strategy", "boxes"), glyphKey = "ch_play"),
        ToolMeta(id = "game-nonogram", number = 177, chapterId = 16, category = ToolCategory.GAMES, name = "Nonogram (Picross 5×5)", tagline = "Deduce hidden pixel patterns from row & column run-length clues.", keywords = listOf("nonogram", "picross", "griddler", "logic", "pixel puzzle"), glyphKey = "ch_play"),
        ToolMeta(id = "game-maze", number = 178, chapterId = 16, category = ToolCategory.GAMES, name = "Maze Runner & BFS Hint", tagline = "Procedural DFS labyrinth generator with live BFS shortest-path solver.", keywords = listOf("maze", "labyrinth", "bfs", "pathfinder", "puzzle"), glyphKey = "ch_play"),
        ToolMeta(id = "game-flappy", number = 179, chapterId = 16, category = ToolCategory.GAMES, name = "Canvas Tap Flyer", tagline = "60fps arcade gravity flyer through randomized pipe gaps.", keywords = listOf("flappy", "flyer", "arcade", "tap", "high score"), glyphKey = "ch_play"),
        ToolMeta(id = "game-whack", number = 180, chapterId = 16, category = ToolCategory.GAMES, name = "Whack-a-Mole Sprint", tagline = "30-second high-tempo 3×3 arcade reflex & hit-streak sprint.", keywords = listOf("whack a mole", "arcade", "reflex", "sprint", "tap"), glyphKey = "ch_play"),
        ToolMeta(id = "game-scramble", number = 181, chapterId = 16, category = ToolCategory.GAMES, name = "Word Scramble Blitz", tagline = "Unscramble shuffled words with letter-reveal hints & streak counter.", keywords = listOf("word scramble", "anagram", "word game", "puzzle", "letters"), glyphKey = "ch_play"),
        ToolMeta(id = "game-party", number = 182, chapterId = 16, category = ToolCategory.GAMES, name = "Party Hub (Imposter & Bingo)", tagline = "Pass-and-play Imposter word game, 1–75 Bingo caller & Truth/Dare.", keywords = listOf("party", "imposter", "bingo", "truth or dare", "group", "multiplayer"), glyphKey = "ch_play"),

        // Health (+3)
        ToolMeta(id = "hlth-cycle", number = 183, chapterId = 17, category = ToolCategory.HEALTH, name = "Cycle & Ovulation Log", tagline = "Track cycle day, predict next period/ovulation & log symptoms offline.", keywords = listOf("cycle", "period", "ovulation", "fertility", "symptoms", "health"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-workout", number = 184, chapterId = 17, category = ToolCategory.HEALTH, name = "Workout Set & Volume Log", tagline = "Log sets×reps×weight, calculate total volume & Epley 1RM in SQLite.", keywords = listOf("workout", "gym", "sets", "reps", "weight", "1rm", "lifting"), requires = setOf(CapabilityRequirement.STORAGE), glyphKey = "ch_body"),
        ToolMeta(id = "hlth-posture", number = 185, chapterId = 17, category = ToolCategory.HEALTH, name = "Guided Posture & Stretch Coach", tagline = "Timed 5-drill desk ergonomics & neck/shoulder mobility routine with chimes.", keywords = listOf("posture", "stretch", "ergonomics", "neck", "back", "desk", "mobility"), glyphKey = "ch_body"),

        // Human Benchmark (+3)
        ToolMeta(id = "bench-matrix", number = 186, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Pattern Matrix Speed", tagline = "Spot the odd geometric shape across expanding 3×3–5×5 timed grids.", keywords = listOf("pattern", "matrix", "iq", "visual scan", "benchmark", "shapes"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-readwpm", number = 187, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Reading Speed (WPM) & Quiz", tagline = "Timed prose reader paired with a comprehension verification check.", keywords = listOf("reading speed", "wpm", "comprehension", "quiz", "benchmark"), glyphKey = "ch_mind"),
        ToolMeta(id = "bench-gonogo", number = 188, chapterId = 20, category = ToolCategory.BENCHMARK, name = "Go / No-Go Impulse Control", tagline = "Tap fast on green GO cues while inhibiting false alarms on red NO-GO.", keywords = listOf("go no go", "impulse", "inhibition", "reaction", "cognitive", "benchmark"), glyphKey = "ch_mind"),

        // Social & Web (+3)
        ToolMeta(id = "soc-thread", number = 189, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.TEXT), name = "Social Thread Splitter", tagline = "Split long essays cleanly into numbered posts for X, Threads & Bluesky.", keywords = listOf("thread", "splitter", "twitter", "x", "threads", "bluesky", "posts"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-giveaway", number = 190, chapterId = 18, category = ToolCategory.SOCIAL, name = "Fair Giveaway Winner Picker", tagline = "CSPRNG SecureRandom multi-winner draw from handles with audit seed.", keywords = listOf("giveaway", "winner", "raffle", "random", "picker", "contest"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),
        ToolMeta(id = "soc-serp", number = 191, chapterId = 18, category = ToolCategory.SOCIAL, secondaryCategories = setOf(ToolCategory.DEV), name = "Google SERP & Pixel Preview", tagline = "Simulate desktop/mobile search snippets with live title/meta px cutoffs.", keywords = listOf("serp", "seo", "google", "meta description", "title", "preview", "snippet"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_social"),

        // Developer (+3)
        ToolMeta(id = "dev-sqlfmt", number = 192, chapterId = 19, category = ToolCategory.DEV, name = "SQL Formatter & Schema Linter", tagline = "Upper-case/indent SQL queries & detect syntax or DDL anti-patterns.", keywords = listOf("sql", "formatter", "linter", "database", "query", "schema", "ddl"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-cert", number = 193, chapterId = 19, category = ToolCategory.DEV, name = "X.509 PEM & Base32 / Hex Lab", tagline = "Inspect PEM SSL certificates, encode/decode Base32 & view byte hex dumps.", keywords = listOf("x509", "pem", "certificate", "ssl", "base32", "hex dump", "security"), io = ToolIO(accepts = setOf(DataKind.TEXT), produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev"),
        ToolMeta(id = "dev-httpref", number = 194, chapterId = 19, category = ToolCategory.DEV, name = "HTTP Status, MIME & Ports Ref", tagline = "Instant offline reference for HTTP 1xx–5xx codes, MIME types & TCP ports.", keywords = listOf("http", "status code", "mime", "ports", "tcp", "reference", "cheat sheet"), io = ToolIO(produces = setOf(DataKind.TEXT)), glyphKey = "ch_dev")
    )

    val LIVE_TOOLS: List<ToolMeta>
        get() = ALL_TOOLS

    fun getToolById(id: String): ToolMeta? = ALL_TOOLS.firstOrNull { it.id == id }

    fun getToolsByCategory(category: ToolCategory): List<ToolMeta> =
        if (category == ToolCategory.ALL) ALL_TOOLS
        else ALL_TOOLS.filter { it.category == category || category in it.secondaryCategories }

    fun findCompatibleSendToTargets(sourceToolId: String, outputKind: DataKind): List<ToolMeta> =
        ALL_TOOLS.filter { it.id != sourceToolId && outputKind in it.io.accepts }

    fun searchTools(query: String, category: ToolCategory = ToolCategory.ALL): List<ToolMeta> {
        val pool = getToolsByCategory(category)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return pool
        val terms = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return pool.filter { tool ->
            val searchable = buildString {
                append(tool.name.lowercase()).append(' ')
                append(tool.tagline.lowercase()).append(' ')
                append(tool.category.label.lowercase()).append(' ')
                append(tool.secondaryCategories.joinToString(" ") { it.label.lowercase() }).append(' ')
                append(tool.keywords.joinToString(" ").lowercase()).append(' ')
                append(tool.aliases.joinToString(" ").lowercase())
            }
            terms.all { term -> searchable.contains(term) }
        }
    }
}
