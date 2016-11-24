/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.aries.osgi.functional.internal;

import org.apache.aries.osgi.functional.OSGi;
import org.apache.aries.osgi.functional.OSGiResult;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Carlos Sierra Andrés
 */
public class PrototypesMOSGi<T>
	extends MOSGiImpl<ServiceObjects<T>> {

	private final String _filterString;

	private final Class<T> _clazz;

	public PrototypesMOSGi(Class<T> clazz, String filterString) {
		super(bundleContext -> {
			Pipe<Tuple<ServiceObjects<T>>, Tuple<ServiceObjects<T>>> added =
				Pipe.create();

			Pipe<Tuple<ServiceObjects<T>>, Tuple<ServiceObjects<T>>>
				removed = Pipe.create();

			Consumer<Tuple<ServiceObjects<T>>> addedSource =
				added.getSource();

			Consumer<Tuple<ServiceObjects<T>>> removedSource =
				removed.getSource();

			ServiceTracker<T, Tuple<ServiceObjects<T>>> serviceTracker =
				new ServiceTracker<>(
					bundleContext,
					OSGiImpl.buildFilter(
						bundleContext, filterString, clazz),
					new ServiceTrackerCustomizer
						<T, Tuple<ServiceObjects<T>>>() {

						@Override
						public Tuple<ServiceObjects<T>> addingService(
							ServiceReference<T> reference) {

							ServiceObjects<T> serviceObjects =
								bundleContext.getServiceObjects(reference);

							Tuple<ServiceObjects<T>> tuple =
								Tuple.create(serviceObjects);

							addedSource.accept(tuple);

							return tuple;
						}

						@Override
						public void modifiedService(
							ServiceReference<T> reference,
							Tuple<ServiceObjects<T>> service) {

							removedService(reference, service);

							addingService(reference);
						}

						@Override
						public void removedService(
							ServiceReference<T> reference,
							Tuple<ServiceObjects<T>> tuple) {

							removedSource.accept(tuple);
						}
					});

			return new OSGiResultImpl<>(
				added, removed, serviceTracker::open,
				serviceTracker::close);
		});

		_filterString = filterString;
		_clazz = clazz;
	}

	@Override
	public <S> OSGiImpl<S> flatMap(
		Function<ServiceObjects<T>, OSGi<S>> fun) {
		return new OSGiImpl<>(bundleContext -> {
			Pipe<Tuple<S>, Tuple<S>> added = Pipe.create();

			Pipe<Tuple<S>, Tuple<S>> removed = Pipe.create();

			Consumer<Tuple<S>> addedSource = added.getSource();

			Consumer<Tuple<S>> removedSource = removed.getSource();

			ServiceTracker<T, Tracked<ServiceObjects<T>, S>>
				serviceTracker = new ServiceTracker<>(
				bundleContext,
				buildFilter(bundleContext, _filterString, _clazz),
				new ServiceTrackerCustomizer
					<T, Tracked<ServiceObjects<T>, S>>() {

					@Override
					public Tracked<ServiceObjects<T>, S> addingService(
						ServiceReference<T> reference) {

						ServiceObjects<T> serviceObjects =
							bundleContext.getServiceObjects(
								reference);

						OSGi<S> program = fun.apply(serviceObjects);

						Tracked<ServiceObjects<T>, S> tracked =
							new Tracked<>();

						OSGiResult<S> result = program.run(
							bundleContext, s -> {
								Tuple<S> tuple = Tuple.create(s);

								tracked.result = tuple;

								addedSource.accept(tuple);
							}
						);

						tracked.program = result;
						tracked.service = serviceObjects;

						return tracked;
					}

					@Override
					public void modifiedService(
						ServiceReference<T> reference,
						Tracked<ServiceObjects<T>, S> tracked) {

						removedService(reference, tracked);

						addingService(reference);
					}

					@Override
					public void removedService(
						ServiceReference<T> reference,
						Tracked<ServiceObjects<T>, S> tracked) {

						tracked.program.close();

						if (tracked.result != null) {
							removedSource.accept(tracked.result);
						}
					}
				});

			return new OSGiResultImpl<>(
				added, removed, serviceTracker::open,
				serviceTracker::close);
		});
	}

}
