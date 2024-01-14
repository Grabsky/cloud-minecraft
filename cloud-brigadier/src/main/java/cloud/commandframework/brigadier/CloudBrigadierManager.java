//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package cloud.commandframework.brigadier;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.SenderMapper;
import cloud.commandframework.SenderMapperHolder;
import cloud.commandframework.arguments.flags.CommandFlagParser;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.standard.BooleanParser;
import cloud.commandframework.arguments.standard.ByteParser;
import cloud.commandframework.arguments.standard.DoubleParser;
import cloud.commandframework.arguments.standard.FloatParser;
import cloud.commandframework.arguments.standard.IntegerParser;
import cloud.commandframework.arguments.standard.LongParser;
import cloud.commandframework.arguments.standard.ShortParser;
import cloud.commandframework.arguments.standard.StringArrayParser;
import cloud.commandframework.arguments.standard.StringParser;
import cloud.commandframework.arguments.suggestion.SuggestionProvider;
import cloud.commandframework.brigadier.argument.ArgumentTypeFactory;
import cloud.commandframework.brigadier.argument.BrigadierMapping;
import cloud.commandframework.brigadier.argument.BrigadierMappingBuilder;
import cloud.commandframework.brigadier.argument.BrigadierMappings;
import cloud.commandframework.brigadier.parser.WrappedBrigadierParser;
import cloud.commandframework.brigadier.node.LiteralBrigadierNodeFactory;
import cloud.commandframework.brigadier.suggestion.TooltipSuggestion;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.setting.Configurable;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.leangen.geantyref.GenericTypeReflector;
import io.leangen.geantyref.TypeToken;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Manager used to map cloud {@link Command}
 * <p>
 * The structure of this class is largely inspired by
 * <a href="https://github.com/aikar/commands/blob/master/brigadier/src/main/java/co.aikar.commands/ACFBrigadierManager.java">
 * ACFBrigadiermanager</a> in the ACF project, which was originally written by MiniDigger and licensed under the MIT license.
 *
 * @param <C> cloud command sender type
 * @param <S> brigadier command source type
 */
@SuppressWarnings({"unchecked", "unused"})
public final class CloudBrigadierManager<C, S> implements SenderMapperHolder<S, C> {

    private final BrigadierMappings<C, S> brigadierMappings = BrigadierMappings.create();
    private final LiteralBrigadierNodeFactory<C, S> literalBrigadierNodeFactory;
    private final Map<@NonNull Class<?>, @NonNull ArgumentTypeFactory<?>> defaultArgumentTypeSuppliers;
    private final Configurable<BrigadierSetting> settings = Configurable.enumConfigurable(BrigadierSetting.class);
    private final SenderMapper<S, C> brigadierSourceMapper;

    /**
     * Create a new cloud brigadier manager
     *
     * @param commandManager        Command manager
     * @param dummyContextProvider  Provider of dummy context for completions
     * @param brigadierSourceMapper Mapper between the Brigadier command source type and cloud command sender type
     */
    public CloudBrigadierManager(
            final @NonNull CommandManager<C> commandManager,
            final @NonNull Supplier<@NonNull CommandContext<C>> dummyContextProvider,
            final @NonNull SenderMapper<S, C> brigadierSourceMapper
    ) {
        this.brigadierSourceMapper = Objects.requireNonNull(brigadierSourceMapper, "brigadierSourceMapper");
        this.defaultArgumentTypeSuppliers = new HashMap<>();
        this.literalBrigadierNodeFactory = new LiteralBrigadierNodeFactory<>(
                this,
                commandManager,
                dummyContextProvider,
                commandManager.suggestionFactory().mapped(TooltipSuggestion::tooltipSuggestion)
        );
        this.registerInternalMappings();
        commandManager.registerCommandPreProcessor(ctx -> {
            ctx.commandContext().store(
                    WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER,
                    this.brigadierSourceMapper.reverse(ctx.commandContext().sender())
            );
        });
    }

    private void registerInternalMappings() {
        /* Map byte, short and int to IntegerArgumentType */
        this.registerMapping(new TypeToken<ByteParser<C>>() {
        }, builder -> builder.to(argument -> IntegerArgumentType.integer(argument.min(), argument.max())).cloudSuggestions());
        this.registerMapping(new TypeToken<ShortParser<C>>() {
        }, builder -> builder.to(argument -> IntegerArgumentType.integer(argument.min(), argument.max())).cloudSuggestions());
        this.registerMapping(new TypeToken<IntegerParser<C>>() {
        }, builder -> builder.to(argument -> {
            if (!argument.hasMin() && !argument.hasMax()) {
                return IntegerArgumentType.integer();
            }
            if (argument.hasMin() && !argument.hasMax()) {
                return IntegerArgumentType.integer(argument.min());
            } else if (!argument.hasMin()) {
                // Brig uses Integer.MIN_VALUE and Integer.MAX_VALUE for default min/max
                return IntegerArgumentType.integer(Integer.MIN_VALUE, argument.max());
            }
            return IntegerArgumentType.integer(argument.min(), argument.max());
        }).cloudSuggestions());
        /* Map float to FloatArgumentType */
        this.registerMapping(new TypeToken<FloatParser<C>>() {
        }, builder -> builder.to(argument -> {
            if (!argument.hasMin() && !argument.hasMax()) {
                return FloatArgumentType.floatArg();
            }
            if (argument.hasMin() && !argument.hasMax()) {
                return FloatArgumentType.floatArg(argument.min());
            } else if (!argument.hasMin()) {
                // Brig uses -Float.MAX_VALUE and Float.MAX_VALUE for default min/max
                return FloatArgumentType.floatArg(-Float.MAX_VALUE, argument.max());
            }
            return FloatArgumentType.floatArg(argument.min(), argument.max());
        }).cloudSuggestions());
        /* Map double to DoubleArgumentType */
        this.registerMapping(new TypeToken<DoubleParser<C>>() {
        }, builder -> builder.to(argument -> {
            if (!argument.hasMin() && !argument.hasMax()) {
                return DoubleArgumentType.doubleArg();
            }
            if (argument.hasMin() && !argument.hasMax()) {
                return DoubleArgumentType.doubleArg(argument.min());
            } else if (!argument.hasMin()) {
                // Brig uses -Double.MAX_VALUE and Double.MAX_VALUE for default min/max
                return DoubleArgumentType.doubleArg(-Double.MAX_VALUE, argument.max());
            }
            return DoubleArgumentType.doubleArg(argument.min(), argument.max());
        }).cloudSuggestions());
        /* Map long parser to LongArgumentType */
        this.registerMapping(new TypeToken<LongParser<C>>() {
        }, builder -> builder.to(longParser -> {
            if (!longParser.hasMin() && !longParser.hasMax()) {
                return LongArgumentType.longArg();
            }
            if (longParser.hasMin() && !longParser.hasMax()) {
                return LongArgumentType.longArg(longParser.min());
            } else if (!longParser.hasMin()) {
                // Brig uses Long.MIN_VALUE and Long.MAX_VALUE for default min/max
                return LongArgumentType.longArg(Long.MIN_VALUE, longParser.max());
            }
            return LongArgumentType.longArg(longParser.min(), longParser.max());
        }).cloudSuggestions());
        /* Map boolean to BoolArgumentType */
        this.registerMapping(new TypeToken<BooleanParser<C>>() {
        }, builder -> builder.toConstant(BoolArgumentType.bool()));
        /* Map String properly to StringArgumentType */
        this.registerMapping(new TypeToken<StringParser<C>>() {
        }, builder -> builder.cloudSuggestions().to(argument -> {
            switch (argument.stringMode()) {
                case QUOTED:
                    return StringArgumentType.string();
                case GREEDY:
                case GREEDY_FLAG_YIELDING:
                    return StringArgumentType.greedyString();
                default:
                    return StringArgumentType.word();
            }
        }));
        /* Map flags to a greedy string */
        this.registerMapping(new TypeToken<CommandFlagParser<C>>() {
        }, builder -> builder.cloudSuggestions().toConstant(StringArgumentType.greedyString()));
        /* Map String[] to a greedy string */
        this.registerMapping(new TypeToken<StringArrayParser<C>>() {
        }, builder -> builder.cloudSuggestions().toConstant(StringArgumentType.greedyString()));
        /* Map wrapped parsers to their native types */
        this.registerMapping(new TypeToken<WrappedBrigadierParser<C, ?>>() {
        }, builder -> builder.to(WrappedBrigadierParser::nativeArgumentType));
    }

    /**
     * Returns a {@link Configurable} instance that can be used to modify the settings for this instance.
     *
     * @return settings instance
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull Configurable<BrigadierSetting> settings() {
        return this.settings;
    }

    @Override
    public @NonNull SenderMapper<S, C> senderMapper() {
        return this.brigadierSourceMapper;
    }

    /**
     * Sets whether Brigadier's native suggestions for number types will be used, or if cloud's number suggestions should be
     * used instead. At the time of writing the native suggestions are equivalent to
     * {@link SuggestionProvider#noSuggestions()}.
     *
     * <p>The default is to use cloud's suggestions, or {@code false}.</p>
     *
     * @param nativeNumberSuggestions whether Brigadier suggestions should be used for number types
     * @since 1.2.0
     */
    @API(status = API.Status.STABLE, since = "1.2.0")
    public void setNativeNumberSuggestions(final boolean nativeNumberSuggestions) {
        this.setNativeSuggestions(new TypeToken<ByteParser<C>>() {}, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<ShortParser<C>>() {}, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<IntegerParser<C>>() {}, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<FloatParser<C>>() {}, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<DoubleParser<C>>() {}, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<LongParser<C>>() {}, nativeNumberSuggestions);
    }

    /**
     * Set whether to use Brigadier's native suggestions for an argument type with an already registered mapper.
     * <p>
     * If Brigadier's suggestions are not used, suggestions will fall back to the cloud suggestion provider.
     *
     * @param argumentType      cloud argument parser type
     * @param nativeSuggestions whether Brigadier suggestions should be used
     * @param <T>               argument type
     * @param <K>               cloud argument parser type
     * @throws IllegalArgumentException when there is no mapper registered for the provided argument type
     * @since 1.2.0
     */
    @API(status = API.Status.STABLE, since = "1.2.0")
    public <T, K extends ArgumentParser<C, T>> void setNativeSuggestions(
            final @NonNull TypeToken<K> argumentType,
            final boolean nativeSuggestions
    ) throws IllegalArgumentException {
        final Class<K> parserClass = (Class<K>) GenericTypeReflector.erase(argumentType.getType());
        final BrigadierMapping<C, K, S> mapping = this.brigadierMappings.mapping(parserClass);
        if (mapping == null) {
            throw new IllegalArgumentException(
                    "No mapper registered for type: " + GenericTypeReflector
                            .erase(argumentType.getType())
                            .toGenericString()
            );
        }
        this.brigadierMappings.registerMapping(parserClass, mapping.withNativeSuggestions(nativeSuggestions));
    }

    /**
     * Register a cloud-Brigadier mapping.
     *
     * @param parserType The cloud argument parser type
     * @param configurer a callback that will configure the mapping attributes
     * @param <K>        cloud argument parser type
     * @since 1.5.0
     */
    @API(status = API.Status.STABLE, since = "1.5.0")
    public <K extends ArgumentParser<C, ?>> void registerMapping(
            final @NonNull TypeToken<K> parserType,
            final Consumer<BrigadierMappingBuilder<K, S>> configurer
    ) {
        final BrigadierMappingBuilder<K, S> builder = BrigadierMapping.builder();
        configurer.accept(builder);
        this.mappings().registerMappingUnsafe((Class<K>) GenericTypeReflector.erase(parserType.getType()), builder.build());
    }

    /**
     * Returns the mappings between Cloud and Brigadier types.
     *
     * @return the mappings
     * @since 2.0.0
     */
    @API(status = API.Status.INTERNAL, since = "2.0.0")
    public @NonNull BrigadierMappings<C, S> mappings() {
        return this.brigadierMappings;
    }

    /**
     * Returns a factory that creates {@link LiteralCommandNode literal command nodes} from Cloud commands.
     *
     * @return the literal node factory
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull LiteralBrigadierNodeFactory<C, S> literalBrigadierNodeFactory() {
        return this.literalBrigadierNodeFactory;
    }

    /**
     * Register a default mapping to between a class and a Brigadier argument type
     *
     * @param <T>     the type
     * @param clazz   the type to map
     * @param factory factory that creates the argument type
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public <T> void registerDefaultArgumentTypeSupplier(
            final @NonNull Class<T> clazz,
            final @NonNull ArgumentTypeFactory<T> factory
    ) {
        this.defaultArgumentTypeSuppliers.put(clazz, factory);
    }

    /**
     * Returns the default argument type factories.
     *
     * @return immutable view of the factories
     * @since 2.0.0
     */
    @API(status = API.Status.INTERNAL, since = "2.0.0")
    public @NonNull Map<@NonNull Class<?>, @NonNull ArgumentTypeFactory<?>> defaultArgumentTypeFactories() {
        return Collections.unmodifiableMap(this.defaultArgumentTypeSuppliers);
    }
}