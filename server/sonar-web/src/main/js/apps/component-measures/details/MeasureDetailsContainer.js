/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import { connect } from 'react-redux';

import MeasureDetails from './MeasureDetails';
import { fetchMeasure } from './actions';

const mapStateToProps = state => {
  return {
    component: state.app.component,
    metrics: state.app.metrics,
    metric: state.details.metric,
    measure: state.details.measure,
    secondaryMeasure: state.details.secondaryMeasure,
    periods: state.details.periods
  };
};

const mapDispatchToProps = dispatch => {
  return {
    fetchMeasure: (metricKey, periodIndex) => dispatch(fetchMeasure(metricKey, periodIndex))
  };
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(MeasureDetails);
