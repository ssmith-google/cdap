/*
 * Copyright © 2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import React, { Component } from 'react';
import { Provider } from 'react-redux';
import PropTypes from 'prop-types';
import PipelineConfigurationsStore, {
  ACTIONS as PipelineConfigurationsActions,
} from 'components/PipelineConfigurations/Store';
import ConfigModelessActionButtons from 'components/PipelineConfigurations/ConfigurationsContent/ConfigModelessActionButtons';
import IconSVG from 'components/IconSVG';
import T from 'i18n-react';
import ConfigurableTab from 'components/ConfigurableTab';
import TabConfig from 'components/PipelineConfigurations/TabConfig';
import { GLOBALS } from 'services/global-constants';
import PipelineModeless from 'components/PipelineDetails/PipelineModeless';

require('./PipelineConfigurations.scss');
require('./ConfigurationsContent/ConfigurationsContent.scss');

const PREFIX = 'features.PipelineConfigurations';

export default class PipelineConfigurations extends Component {
  static propTypes = {
    open: PropTypes.func,
    onClose: PropTypes.func,
    anchorEl: PropTypes.oneOf([PropTypes.element, PropTypes.string]),
    isDetailView: PropTypes.bool,
    isPreview: PropTypes.bool,
    pipelineType: PropTypes.string,
    isHistoricalRun: PropTypes.bool,
    action: PropTypes.string,
    pipelineName: PropTypes.string,
  };

  static defaultProps = {
    isDetailView: false,
    isPreview: false,
    pipelineType: GLOBALS.etlDataPipeline,
  };

  componentDidMount() {
    if (!this.props.isDetailView) {
      return;
    }
    PipelineConfigurationsStore.dispatch({
      type: PipelineConfigurationsActions.SET_MODELESS_OPEN_STATUS,
      payload: { open: true },
    });

    let { pipelineType, isDetailView, isHistoricalRun, isPreview } = this.props;

    PipelineConfigurationsStore.dispatch({
      type: PipelineConfigurationsActions.SET_PIPELINE_VISUAL_CONFIGURATION,
      payload: {
        pipelineVisualConfiguration: {
          pipelineType,
          isDetailView,
          isHistoricalRun,
          isPreview,
        },
      },
    });

    this.storeSubscription = PipelineConfigurationsStore.subscribe(() => {
      let state = PipelineConfigurationsStore.getState();
      if (!state.modelessOpen) {
        this.props.onClose();
        this.storeSubscription();
      }
    });
  }

  componentWillUnmount() {
    if (this.storeSubscription) {
      this.storeSubscription();
    }
  }

  getHeaderLabel() {
    let headerLabel;
    if (this.props.isHistoricalRun) {
      headerLabel = T.translate(`${PREFIX}.titleHistorical`);
    } else {
      headerLabel = T.translate(`${PREFIX}.title`);
      if (this.props.pipelineName.length) {
        headerLabel += ` "${this.props.pipelineName}"`;
      }
    }
    return headerLabel;
  }

  renderHeader() {
    let headerLabel;
    if (this.props.isHistoricalRun) {
      headerLabel = T.translate(`${PREFIX}.titleHistorical`);
    } else {
      headerLabel = T.translate(`${PREFIX}.title`);
      if (this.props.pipelineName.length) {
        headerLabel += ` "${this.props.pipelineName}"`;
      }
    }
    return (
      <div className="pipeline-configurations-header modeless-header">
        <div className="modeless-title">{headerLabel}</div>
        <div className="btn-group">
          <a className="btn" onClick={this.props.onClose} data-testid="close-modeless">
            <IconSVG name="icon-close" />
          </a>
        </div>
      </div>
    );
  }

  render() {
    let tabConfig;
    if (GLOBALS.etlBatchPipelines.includes(this.props.pipelineType)) {
      tabConfig = TabConfig;
    } else if (this.props.pipelineType === GLOBALS.etlDataStreams) {
      tabConfig = { ...TabConfig };
      // Don't show Alerts tab for realtime pipelines
      const alertsTabName = T.translate(`${PREFIX}.Alerts.title`);
      tabConfig.tabs = TabConfig.tabs.filter((tab) => {
        return tab.name !== alertsTabName;
      });
    } else if (this.props.pipelineType === GLOBALS.eltSqlPipeline) {
      tabConfig = { ...TabConfig };
      // Only show pipeline config, compute config, and resource config for now
      let allowed = [
        T.translate(`${PREFIX}.ComputeConfig.title`),
        T.translate(`${PREFIX}.PipelineConfig.title`),
        T.translate(`${PREFIX}.Resources.title`),
      ];
      tabConfig.tabs = TabConfig.tabs.filter((tab) => {
        return allowed.indexOf(tab.name) !== -1;
      });
    }
    return (
      <PipelineModeless
        open={this.props.open}
        anchorEl={this.props.anchorEl}
        onClose={this.props.onClose}
        title={this.getHeaderLabel()}
      >
        <Provider store={PipelineConfigurationsStore}>
          <div
            className="pipeline-configurations-content"
            ref={(ref) => (this.configModeless = ref)}
          >
            <div className="pipeline-config-tabs-wrapper">
              <ConfigurableTab tabConfig={tabConfig} />
              <ConfigModelessActionButtons onClose={this.props.onClose} />
            </div>
          </div>
        </Provider>
      </PipelineModeless>
    );
  }
}
