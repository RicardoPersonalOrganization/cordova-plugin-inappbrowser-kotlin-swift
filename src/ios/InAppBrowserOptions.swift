import Foundation

@objcMembers
public class InAppBrowserOptions: NSObject {
    public var location: Bool = true
    public var toolbar: Bool = true
    public var closebuttoncaption: String? = nil
    public var toolbarposition: String = "bottom"
    public var cleardata: Bool = false
    public var clearcache: Bool = false
    public var clearsessioncache: Bool = false
    public var hidespinner: Bool = false
    public var enableviewportscale: Bool = false
    public var mediaplaybackrequiresuseraction: Bool = false
    public var allowinlinemediaplayback: Bool = false
    public var hidden: Bool = false
    public var disallowoverscroll: Bool = false
    public var hidenavigationbuttons: Bool = false
    public var closebuttoncolor: String? = nil
    public var lefttoright: Bool = false
    public var toolbarcolor: String? = nil
    public var toolbartranslucent: Bool = true
    public var beforeload: String = ""
    public var presentationstyle: String? = nil
    public var transitionstyle: String? = nil

    public static func parseOptions(_ options: String) -> InAppBrowserOptions {
        let obj = InAppBrowserOptions()

        let pairs = options.split(separator: ",")
        for pair in pairs {
            let keyvalue = pair.split(separator: "=")
            if keyvalue.count == 2 {
                let key = keyvalue[0].lowercased()
                let value = String(keyvalue[1])
                let value_lc = value.lowercased()

                let isBoolean = (value_lc == "yes" || value_lc == "no")
                if obj.responds(to: Selector(key)) {
                    if isBoolean {
                        obj.setValue(value_lc == "yes", forKey: key)
                    } else if let number = NumberFormatter().number(from: value_lc) {
                        obj.setValue(number, forKey: key)
                    } else {
                        obj.setValue(value, forKey: key)
                    }
                }
            }
        }

        return obj
    }
}
