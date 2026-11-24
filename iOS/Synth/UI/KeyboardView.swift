import SwiftUI
import UIKit

/// The on-screen piano. Built in UIKit so every finger is tracked on its
/// own: play chords, and slide a finger across keys to glide between notes.
/// Touching lower on a key plays it louder.
struct KeyboardView: UIViewRepresentable {
    var lowestNote: Int
    var onNoteOn: (Int, Float) -> Void
    var onNoteOff: (Int) -> Void

    func makeUIView(context: Context) -> KeyboardUIView {
        let view = KeyboardUIView()
        view.lowestNote = lowestNote
        view.onNoteOn = onNoteOn
        view.onNoteOff = onNoteOff
        return view
    }

    /// Switching tabs removes the keyboard; don't leave notes hanging.
    static func dismantleUIView(_ view: KeyboardUIView, coordinator: ()) {
        view.releaseAll()
    }

    func updateUIView(_ view: KeyboardUIView, context: Context) {
        view.onNoteOn = onNoteOn
        view.onNoteOff = onNoteOff
        if view.lowestNote != lowestNote {
            view.lowestNote = lowestNote
        }
    }
}

final class KeyboardUIView: UIView {
    /// Must be a C (a multiple of 12).
    var lowestNote = 48 {
        didSet {
            releaseAll()
            setNeedsDisplay()
        }
    }

    var onNoteOn: ((Int, Float) -> Void)?
    var onNoteOff: ((Int) -> Void)?

    private var touchNotes: [UITouch: Int] = [:]
    private var heldCounts: [Int: Int] = [:]

    private static let whiteOffsets = [0, 2, 4, 5, 7, 9, 11]
    private static let hasBlackAfter: Set<Int> = [0, 1, 3, 4, 5] // C D F G A
    private static let noteNames = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        isOpaque = false
        backgroundColor = .clear
        contentMode = .redraw
        isAccessibilityElement = true
        accessibilityLabel = "Keyboard"
        accessibilityTraits = .allowsDirectInteraction
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    // MARK: Geometry

    private var whiteKeyCount: Int {
        // Aim for keys ~44 pt wide, always ending on a full octave's worth of white keys or more.
        max(7, Int(bounds.width / 44))
    }

    private var whiteWidth: CGFloat { bounds.width / CGFloat(whiteKeyCount) }
    private var blackWidth: CGFloat { whiteWidth * 0.62 }
    private var blackHeight: CGFloat { bounds.height * 0.6 }

    private func whiteNote(_ index: Int) -> Int {
        lowestNote + 12 * (index / 7) + Self.whiteOffsets[index % 7]
    }

    private func whiteRect(_ index: Int) -> CGRect {
        CGRect(x: CGFloat(index) * whiteWidth, y: 0, width: whiteWidth, height: bounds.height)
    }

    /// Black keys as (note, rect), skipping one that would hang off the right edge.
    private var blackKeys: [(note: Int, rect: CGRect)] {
        (0..<whiteKeyCount - 1).compactMap { i in
            guard Self.hasBlackAfter.contains(i % 7) else { return nil }
            let x = CGFloat(i + 1) * whiteWidth - blackWidth / 2
            return (whiteNote(i) + 1, CGRect(x: x, y: 0, width: blackWidth, height: blackHeight))
        }
    }

    private func hit(_ point: CGPoint) -> (note: Int, velocity: Float)? {
        guard bounds.contains(point) else { return nil }
        if point.y < blackHeight {
            for key in blackKeys where key.rect.contains(point) {
                return (key.note, velocity(point.y, keyHeight: blackHeight))
            }
        }
        let index = min(whiteKeyCount - 1, max(0, Int(point.x / whiteWidth)))
        return (whiteNote(index), velocity(point.y, keyHeight: bounds.height))
    }

    private func velocity(_ y: CGFloat, keyHeight: CGFloat) -> Float {
        Float(0.4 + 0.6 * min(1, max(0, y / keyHeight)))
    }

    // MARK: Touches

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            if let key = hit(touch.location(in: self)) {
                touchNotes[touch] = key.note
                press(key.note, velocity: key.velocity)
            }
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let key = hit(touch.location(in: self))
            let old = touchNotes[touch]
            guard key?.note != old else { continue }
            if let old { release(old) }
            if let key {
                touchNotes[touch] = key.note
                press(key.note, velocity: key.velocity)
            } else {
                touchNotes[touch] = nil
            }
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            if let note = touchNotes.removeValue(forKey: touch) { release(note) }
        }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        touchesEnded(touches, with: event)
    }

    private func press(_ note: Int, velocity: Float) {
        heldCounts[note, default: 0] += 1
        onNoteOn?(note, velocity)
        setNeedsDisplay()
    }

    private func release(_ note: Int) {
        guard let count = heldCounts[note] else { return }
        if count <= 1 {
            heldCounts[note] = nil
            onNoteOff?(note)
        } else {
            heldCounts[note] = count - 1
        }
        setNeedsDisplay()
    }

    func releaseAll() {
        for note in heldCounts.keys { onNoteOff?(note) }
        heldCounts.removeAll()
        touchNotes.removeAll()
        setNeedsDisplay()
    }

    // MARK: Drawing

    override func draw(_ rect: CGRect) {
        let accent = UIColor(Theme.accent)
        let ivory = UIColor(red: 0.96, green: 0.94, blue: 0.89, alpha: 1)
        let ivoryShade = UIColor(red: 0.84, green: 0.81, blue: 0.75, alpha: 1)

        for i in 0..<whiteKeyCount {
            let note = whiteNote(i)
            let keyRect = whiteRect(i).insetBy(dx: 1, dy: 0)
            let path = UIBezierPath(roundedRect: keyRect,
                                    byRoundingCorners: [.bottomLeft, .bottomRight],
                                    cornerRadii: CGSize(width: 5, height: 5))
            (heldCounts[note] != nil ? accent : ivory).setFill()
            path.fill()

            // Shading along the bottom lip.
            ivoryShade.withAlphaComponent(heldCounts[note] != nil ? 0 : 0.6).setFill()
            UIRectFill(CGRect(x: keyRect.minX, y: keyRect.maxY - 6, width: keyRect.width, height: 3))

            if note % 12 == 0 {
                let name = "C\(note / 12 - 1)" as NSString
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 10, weight: .semibold),
                    .foregroundColor: UIColor.black.withAlphaComponent(0.45),
                ]
                let size = name.size(withAttributes: attrs)
                name.draw(at: CGPoint(x: keyRect.midX - size.width / 2, y: keyRect.maxY - size.height - 10),
                          withAttributes: attrs)
            }
        }

        for key in blackKeys {
            let path = UIBezierPath(roundedRect: key.rect,
                                    byRoundingCorners: [.bottomLeft, .bottomRight],
                                    cornerRadii: CGSize(width: 4, height: 4))
            if heldCounts[key.note] != nil {
                accent.setFill()
                path.fill()
            } else {
                UIColor(white: 0.1, alpha: 1).setFill()
                path.fill()
                UIColor(white: 0.24, alpha: 1).setFill()
                UIBezierPath(roundedRect: key.rect.insetBy(dx: 3, dy: 0).offsetBy(dx: 0, dy: -6),
                             cornerRadius: 3).fill()
            }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        setNeedsDisplay()
    }
}
