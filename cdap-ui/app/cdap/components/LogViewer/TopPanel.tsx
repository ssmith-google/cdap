/*
 * Copyright © 2020 Cask Data, Inc.
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

import * as React from 'react';
import withStyles, { WithStyles, StyleRules } from '@material-ui/core/styles/withStyles';
import DataFetcher from 'components/LogViewer/DataFetcher';
import Button from '@material-ui/core/Button';
import FormControlLabel from '@material-ui/core/FormControlLabel';
import Checkbox from '@material-ui/core/Checkbox';
import classnames from 'classnames';
import IconButton from '@material-ui/core/IconButton';
import Close from '@material-ui/icons/Close';
import ArrowDownward from '@material-ui/icons/ArrowDownward';
import If from 'components/If';

export const TOP_PANEL_HEIGHT = '50px';

const styles = (theme): StyleRules => {
  return {
    root: {
      backgroundColor: theme.palette.grey[400],
      color: theme.palette.white[50],
      display: 'flex',
      justifyContent: 'flex-end',
      alignItems: 'center',
      height: TOP_PANEL_HEIGHT,
      paddingLeft: '20px',
      paddingRight: '20px',
      position: 'relative',
    },
    actionButton: {
      margin: theme.spacing(1),
      backgroundColor: theme.palette.white[50],
      color: theme.palette.blue[100],

      '&:hover': {
        // color: theme.palette.white[50],
        color: theme.palette.blue[100],
        // borderColor: theme.palette.white[50],
        backgroundColor: theme.palette.white[50],
      },

      '&:focus': {
        color: theme.palette.blue[100],
        outline: 'none',
        textDecoration: 'none',
      },

      '&:active': {
        color: theme.palette.blue[100],
      },

      '&$disabled': {
        // needed to beat specificity
        color: theme.palette.grey[50],
        cursor: 'not-allowed',
        backgroundColor: theme.palette.white[50],
      },
    },
    downArrow: {
      marginLeft: '5px',
    },
    closeButton: {
      marginLeft: '30px',
    },
    disabled: {},
    checkboxContainer: {
      margin: '0 40px',
      userSelect: 'none',
    },
    checkbox: {
      color: theme.palette.white[50],
    },
  };
};

interface ITopPanelProps extends WithStyles<typeof styles> {
  dataFetcher: DataFetcher;
  isPolling: boolean;
  getLatestLogs: () => void;
  setSystemLogs: (includeSystemLogs: boolean) => void;
  onClose?: () => void;
}

const TopPanelView: React.FC<ITopPanelProps> = ({
  classes,
  dataFetcher,
  isPolling,
  getLatestLogs,
  setSystemLogs,
  onClose,
}) => {
  const [includeSystemLogs, setLocalIncludeSystemLogs] = React.useState(
    dataFetcher.getIncludeSystemLogs()
  );

  function getRawLogsBasePath() {
    const backendUrl = dataFetcher.getRawLogsUrl();
    const encodedBackendUrl = encodeURIComponent(backendUrl);

    const url = `/downloadLogs?backendPath=${encodedBackendUrl}`;
    return url;
  }

  function getRawLogsUrl() {
    return `${getRawLogsBasePath()}&type=raw`;
  }

  function getDownloadLogsUrl() {
    const fileName = dataFetcher.getDownloadFileName();
    return `${getRawLogsBasePath()}&type=download&filename=${fileName}.log`;
  }

  function handleToggleSystemLogs() {
    const newState = !includeSystemLogs;
    setLocalIncludeSystemLogs(newState);
    setSystemLogs(newState);
  }

  return (
    <div className={classes.root}>
      <FormControlLabel
        control={
          <Checkbox
            checked={includeSystemLogs}
            onChange={handleToggleSystemLogs}
            color="default"
            className={classes.checkbox}
          />
        }
        label="Include System Logs"
        className={classes.checkboxContainer}
      />

      <Button
        variant="contained"
        className={classnames(classes.actionButton, { [classes.disabled]: isPolling })}
        disabled={isPolling}
        onClick={getLatestLogs}
      >
        Scroll to Latest Logs
        <ArrowDownward className={classes.downArrow} />
      </Button>
      <Button
        variant="contained"
        className={classes.actionButton}
        href={getRawLogsUrl()}
        target="_blank"
      >
        View Advanced Logs
      </Button>
      <Button
        variant="contained"
        className={classes.actionButton}
        href={getDownloadLogsUrl()}
        target="_blank"
      >
        Download All
      </Button>
      <If condition={typeof onClose === 'function'}>
        <IconButton className={classes.closeButton} onClick={onClose}>
          <Close />
        </IconButton>
      </If>
    </div>
  );
};

const TopPanel = withStyles(styles)(TopPanelView);
export default TopPanel;
