/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ngrinder.security;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.ngrinder.extension.OnLoginRunnable;
import org.ngrinder.infra.plugin.PluginManager;
import org.ngrinder.model.Role;
import org.ngrinder.model.User;
import org.ngrinder.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.ShaPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Date;

import static java.util.Arrays.asList;

/**
 * nGrinder UserDetailsAuthenticationProvider.
 *
 * This class validates the user provided ID / Password from login page. Internally it uses the plugins implementing
 * {@link OnLoginRunnable}. If you want to extend user authentification, please create the plugin implementing
 * {@link OnLoginRunnable} interface.
 *
 * @since 3.0
 */
@SuppressWarnings("UnusedDeclaration")
@Service("ngrinderAuthenticationProvider")
public class NGrinderAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

	protected static final Logger LOG = LoggerFactory.getLogger(NGrinderAuthenticationProvider.class);

	@Getter
	@Setter
	private PluginManager pluginManager;

	private DefaultLoginPlugin defaultLoginPlugin;

	private DefaultLdapLoginPlugin defaultLdapLoginPlugin;

	@Getter(AccessLevel.PROTECTED)
	private ShaPasswordEncoder passwordEncoder;

	@Getter(AccessLevel.PROTECTED)
	@Setter
	private UserDetailsService userDetailsService;

	private UserService userService;

	public NGrinderAuthenticationProvider(PluginManager pluginManager, DefaultLoginPlugin defaultLoginPlugin, DefaultLdapLoginPlugin defaultLdapLoginPlugin,
										  @Lazy ShaPasswordEncoder passwordEncoder, UserDetailsService userDetailsService, UserService userService) {
		this.pluginManager = pluginManager;
		this.defaultLoginPlugin = defaultLoginPlugin;
		this.defaultLdapLoginPlugin = defaultLdapLoginPlugin;
		this.passwordEncoder = passwordEncoder;
		this.userDetailsService = userDetailsService;
		this.userService = userService;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void additionalAuthenticationChecks(UserDetails userDetails,
			UsernamePasswordAuthenticationToken authentication) {

		Authentication authentication2 = SecurityContextHolder.getContext().getAuthentication();
		if (authentication2 != null) {
			return;
		}

		String message = messages.getMessage("AbstractUserDetailsAuthenticationProvider.badCredentials",
				"Bad credentials");
		if (authentication.getCredentials() == null) {
			LOG.debug("Authentication failed: no credentials provided");
			throw new BadCredentialsException(message);
		}

		String presentedPassword = authentication.getCredentials().toString();
		SecuredUser user = ((SecuredUser) userDetails);
		boolean authorized = false;

		for (OnLoginRunnable each : getPluginManager().getEnabledModulesByClass(OnLoginRunnable.class, asList(defaultLdapLoginPlugin, defaultLoginPlugin))) {
			if (each.getClass().getName().equals(user.getAuthProviderClass())) {
				authorized = each.validateUser(user.getUsername(), presentedPassword, user.getPassword(), passwordEncoder, user.getUsername());
				break;
			}
		}

		if (!authorized) {
			LOG.info("{} is not logined by {}", user.getUsername(), user.getAuthProviderClass());
			throw new BadCredentialsException(message);
		}
		LOG.info("{} is logined by {}", user.getUsername(), user.getAuthProviderClass());

		// If It's the first time to login
		if (user.getUser().getId() == null) {
			addNewUserIntoLocal(user);
			LOG.info("{} is saved by password {}", user.getUser().getUserId(), user.getUser().getPassword());
		} else {
			// update user information without followers
			userService.saveWithoutFollowers(user.getUser());
		}

		// Set eager fetched user object to security authentication principal to avoid lazy initialize exception in the another access part.
		User userWithEagerFetch = userService.getOneWithEagerFetch(user.getUser().getUserId());
		user.setUser(userWithEagerFetch);
	}

	/**
	 * Add new user into local db.
	 *
	 * @param securedUser user
	 */
	@Transactional
	public void addNewUserIntoLocal(SecuredUser securedUser) {
		User user = securedUser.getUser();
		user.setAuthProviderClass(securedUser.getUserInfoProviderClass());
		user.setCreatedDate(new Date());
		User newUser = userService.getOne(user.getUserId());
		if (newUser != null) {
			user = newUser.merge(user);
		}
		if (user.getRole() == null) {
			user.setRole(Role.USER);
		}
		userService.save(user);
	}

	@Override
	protected void doAfterPropertiesSet() throws Exception {
		Assert.notNull(this.userDetailsService, "A UserDetailsService must be set");
	}

	@Override
	protected final UserDetails retrieveUser(String username, UsernamePasswordAuthenticationToken authentication) {
		UserDetails loadedUser;

		try {
			loadedUser = this.getUserDetailsService().loadUserByUsername(username);
		} catch (UsernameNotFoundException notFound) {
			throw notFound;
		} catch (Exception repositoryProblem) {
			throw new AuthenticationServiceException(repositoryProblem.getMessage(), repositoryProblem);
		}

		if (loadedUser == null) {
			throw new AuthenticationServiceException(
					"UserDetailsService returned null, which is an interface contract violation");
		}
		return loadedUser;
	}

}
