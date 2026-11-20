import Foundation

/// Swift view of a sound parameter. The names, ranges, defaults and value
/// text all come from the shared C++ engine, so Android will show the same.
struct ParamInfo {
    let id: se_param
    let key: String
    let name: String
    let min: Float
    let max: Float
    let defaultValue: Float
    let steps: Int

    var isDiscrete: Bool { steps > 0 }
    var isBipolar: Bool { min < 0 }

    func normalized(_ value: Float) -> Float {
        (value - min) / (max - min)
    }

    func format(_ value: Float) -> String {
        var buffer = [CChar](repeating: 0, count: 32)
        se_param_format(id, value, &buffer, Int32(buffer.count))
        return buffer.withUnsafeBufferPointer { String(cString: $0.baseAddress!) }
    }

    static let count = Int(SE_PARAM_COUNT.rawValue)

    static let all: [ParamInfo] = (0..<count).map { index in
        let id = se_param(rawValue: Int32(index))
        var info = se_param_info()
        se_param_get_info(id, &info)
        return ParamInfo(
            id: id,
            key: String(cString: info.key),
            name: String(cString: info.name),
            min: info.min_value,
            max: info.max_value,
            defaultValue: info.default_value,
            steps: Int(info.steps)
        )
    }

    static func of(_ id: se_param) -> ParamInfo {
        all[Int(id.rawValue)]
    }

    static func index(forKey key: String) -> Int? {
        let i = Int(se_param_find(key))
        return i >= 0 ? i : nil
    }
}
