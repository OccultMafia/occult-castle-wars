import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("castle-wars-farmer")
public interface CastleWarsConfig extends Config {

    @ConfigItem(
        keyName = "members",
        name = "Members Account",
        description = "Members hop to world 354; F2P hops to world 383"
    )
    default boolean members() {
        return true;
    }
}
