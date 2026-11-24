/// A premade beat: a few patterns, a song that strings them together, a
/// tempo and swing, and small tweaks to the kit.
///
/// Patterns are written as text, one row per drum, 16 characters each:
///   x = hit, X = accented hit, . = rest (spaces are ignored).
/// Drums are named by their short names: BD SD CP LT HT RS CB CH OH CY.
struct Beat {
    let name: String
    let tempo: Float
    var swing: Float = 0
    let patterns: [[String: String]]
    let song: [Int]
    /// Per drum: [param index: value], params are 0 level, 1 tune, 2 decay, 3 tone.
    var kit: [String: [Int: Float]] = [:]
    /// Free beats load for everyone; the rest are part of the full version.
    var isFree = false

    static func parse(_ row: String) -> [Int] {
        row.compactMap { ch in
            switch ch {
            case "x": 1
            case "X": 2
            case ".": 0
            default: nil
            }
        }
    }

    static let factory: [Beat] = [boomBap, house, electro, halfTime, rock, blank]

    static let boomBap = Beat(
        name: "Boom Bap", tempo: 90, swing: 0.45,
        patterns: [
            [ // A: main groove
                "BD": "X... ...x ..x. ....",
                "SD": ".... X... .... X...",
                "CH": "x.x. x.x. x.x. x.x.",
            ],
            [ // B: groove with a pickup
                "BD": "X... ...x ..x. ..x.",
                "SD": ".... X... .... X..x",
                "CH": "x.x. x.x. x.x. x...",
                "OH": ".... .... .... ..x.",
            ],
            [ // C: tom fill
                "BD": "X... ...x .... ....",
                "SD": ".... X... .... ....",
                "HT": ".... .... x.x. ....",
                "LT": ".... .... .... x.x.",
                "CH": "x.x. x.x. .... ....",
            ],
        ],
        song: [0, 0, 0, 1, 0, 0, 0, 2],
        kit: ["BD": [1: 0.45, 2: 0.6], "SD": [3: 0.7]],
        isFree: true
    )

    static let house = Beat(
        name: "Four on the Floor", tempo: 124, swing: 0.15,
        patterns: [
            [
                "BD": "X... X... X... X...",
                "CP": ".... x... .... x...",
                "CH": "x... x... x... x...",
                "OH": "..x. ..x. ..x. ..x.",
            ],
            [
                "BD": "X... X... X... X...",
                "CP": ".... x... .... x...",
                "OH": "..x. ..x. ..x. ..x.",
                "RS": "...x .... .x.. ...x",
                "CB": ".... ..x. .... x...",
            ],
            [ // build-up
                "BD": "X... X... X... X...",
                "CP": ".... x... x.x. xxXX",
                "OH": "..x. ..x. ..x. ..x.",
                "CY": ".... .... .... ...x",
            ],
        ],
        song: [0, 0, 1, 1, 0, 0, 1, 2],
        kit: ["BD": [2: 0.4], "OH": [2: 0.35]],
        isFree: true
    )

    static let electro = Beat(
        name: "Electro Funk", tempo: 112,
        patterns: [
            [
                "BD": "X... ..x. ..x. ....",
                "SD": ".... X... .... X...",
                "CP": ".... x... .... x...",
                "CH": "Xxxx Xxxx Xxxx Xxxx",
                "CB": "x..x ..x. ..x. x...",
            ],
            [
                "BD": "X... ..x. ..x. .x..",
                "SD": ".... X... .... X...",
                "CP": ".... x... .... x...",
                "CH": "Xxxx Xxxx Xxxx Xx..",
                "OH": ".... .... .... ..x.",
                "LT": ".... .... ..x. .x..",
            ],
        ],
        song: [0, 0, 0, 1],
        kit: ["BD": [2: 0.75], "CB": [0: 0.45]]
    )

    static let halfTime = Beat(
        name: "Half-Time Boom", tempo: 140,
        patterns: [
            [
                "BD": "X... .... ..x. ....",
                "CP": ".... .... X... ....",
                "SD": ".... .... x... ....",
                "CH": "x.x. x.x. x.xx x.x.",
            ],
            [
                "BD": "X... ...x ..x. ....",
                "CP": ".... .... X... ....",
                "SD": ".... .... x... ....",
                "CH": "x.x. xxxx x.x. x.xx",
                "OH": ".... .... .... ..x.",
            ],
        ],
        song: [0, 1],
        kit: ["BD": [1: 0.4, 2: 0.95, 3: 0.35]]
    )

    static let rock = Beat(
        name: "Garage Rock", tempo: 116,
        patterns: [
            [
                "BD": "X... ..x. X... ....",
                "SD": ".... X... .... X...",
                "CH": "x.x. x.x. x.x. x.x.",
            ],
            [
                "BD": "X... ..x. X.x. ....",
                "SD": ".... X... .... X.xx",
                "CH": "x.x. x.x. x.x. x...",
                "CY": "x... .... .... ....",
            ],
        ],
        song: [0, 0, 0, 1],
        kit: ["SD": [1: 0.55, 3: 0.8]]
    )

    static let blank = Beat(name: "Blank", tempo: 120, patterns: [], song: [], isFree: true)
}
