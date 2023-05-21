package io.github.newhoo.restkit.ext.solon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.newhoo.restkit.common.RestItem;
import io.github.newhoo.restkit.ext.solon.language.JavaLanguageResolver;
import io.github.newhoo.restkit.restful.BaseRequestResolver;
import io.github.newhoo.restkit.restful.RequestResolver;
import io.github.newhoo.restkit.restful.ep.RestfulResolverProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * solon service scanner
 *
 * @since 1.0.0
 */
public class SolonApiResolver extends BaseRequestResolver {

    @NotNull
    @Override
    public String getFrameworkName() {
        return "Solon";
    }

    @Override
    public List<RestItem> findRestItemListInModule(Module module, GlobalSearchScope globalSearchScope) {
        return new JavaLanguageResolver().findRestItemListInModule(module, globalSearchScope);
    }

    public static class SolonApiResolverProvider implements RestfulResolverProvider {
        @Override
        public RequestResolver createRequestResolver(@NotNull Project project) {
            return new SolonApiResolver();
        }
    }
}
